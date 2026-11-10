#!/bin/sh
# Verifies every app under apps/ (each directory with an app.properties).
#
#   scripts/verify-apps.sh [--config-only] [debug|release ...]   (default: debug release)
#
# Always: application IDs and deep-link schemes are unique across apps.
# Unless --config-only, for each app's built APK of each build type (aapt2 from the newest
# build-tools): package, label and version match app.properties; no other app's application ID
# appears in its dex; its deep links use only its own scheme; its launcher foreground differs from every other app's; the generated
# foundation_* config resources the shell reads exist, match app.properties and nothing else.
# Prints one OK/FAIL line per check and exits non-zero if any check fails.
set -eu
cd "$(dirname "$0")/.."

config_only=false
if [ "${1:-}" = "--config-only" ]; then config_only=true; shift; fi
types=${*:-debug release}

failed=0
ok() { echo "OK   $*"; }
bad() { echo "FAIL $*"; failed=1; }

# prop FILE KEY: the trimmed value of KEY in a properties file.
prop() { sed -n "s/^[[:space:]]*$2[[:space:]]*=[[:space:]]*//p" "$1" | head -n 1 | sed 's/[[:space:]]*$//'; }

apps=""
for f in apps/*/app.properties; do
  [ -f "$f" ] && apps="$apps $(basename "$(dirname "$f")")"
done
if [ -z "$apps" ]; then
  echo "FAIL no apps found (apps/*/app.properties)"
  exit 1
fi

# Uniqueness across apps.
for key in app.applicationId app.deepLinkScheme; do
  dups=$(for app in $apps; do echo "$(prop "apps/$app/app.properties" "$key") $app"; done \
    | sort | awk '{ if ($1 == prev) { print "'"'"'" $1 "'"'"' is used by both " prevapp " and " $2 } prev = $1; prevapp = $2 }')
  if [ -n "$dups" ]; then
    echo "$dups" | while read -r line; do bad "$key $line"; done
    failed=1
  else
    ok "$key is unique across apps:$apps"
  fi
done

if [ "$config_only" = true ]; then exit "$failed"; fi

sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}
aapt2=$(ls -d "$sdk"/build-tools/*/aapt2 2>/dev/null | sort -V | tail -n 1)
if [ -z "$aapt2" ]; then
  echo "FAIL aapt2 not found under $sdk/build-tools"
  exit 1
fi

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

for type in $types; do
  : > "$tmp/icons-$type"
  for app in $apps; do
    props="apps/$app/app.properties"
    apk=$(ls -t "apps/$app/build/outputs/apk/$type"/*.apk 2>/dev/null | head -n 1 || true)
    if [ -z "$apk" ]; then
      bad "$app $type: no APK built (run ./gradlew :apps:$app:assemble$(echo "$type" | cut -c1 | tr a-z A-Z)$(echo "$type" | cut -c2-))"
      continue
    fi
    id=$(prop "$props" app.applicationId)
    [ "$type" = debug ] && id="$id.debug"
    badging=$("$aapt2" dump badging "$apk")

    echo "$badging" | grep -q "^package: name='$id' " && ok "$app $type: package $id" \
      || bad "$app $type: package is not $id"
    name=$(prop "$props" app.name)
    echo "$badging" | grep -qF "application-label:'$name'" && ok "$app $type: label '$name'" \
      || bad "$app $type: label is not '$name'"
    code=$(prop "$props" app.versionCode); version=$(prop "$props" app.versionName)
    echo "$badging" | grep -q "versionCode='$code' versionName='$version'" && ok "$app $type: version $code ($version)" \
      || bad "$app $type: version is not $code ($version)"
    # Deep links (reset password, catalog items) open the app only through its own scheme.
    scheme=$(prop "$props" app.deepLinkScheme)
    schemes=$("$aapt2" dump xmltree --file AndroidManifest.xml "$apk" | sed -n 's/.*android:scheme([^)]*)="\([^"]*\)".*/\1/p' | sort -u | tr '\n' ' ')
    [ "$schemes" = "$scheme " ] && ok "$app $type: deep links use scheme '$scheme'" \
      || bad "$app $type: deep-link schemes are '$schemes', expected only '$scheme'"

    # Config the shell reads at runtime: must match app.properties, and the generated names must be
    # exactly the ones the shell declares (a renamed field would otherwise read as blank).
    "$aapt2" dump resources "$apk" \
      | awk '/string\/foundation_/ { sub(/.*string\//, ""); name = $1; next }
             name != "" { v = $0; sub(/^[^"]*"/, "", v); sub(/"[^"]*$/, "", v); print name "=" v; name = "" }' \
      | sort > "$tmp/config"
    names=$(cut -d= -f1 "$tmp/config" | tr '\n' ' ')
    expected_names="foundation_api_base_url foundation_app_name foundation_deep_link_scheme foundation_default_features foundation_environment foundation_privacy_policy_url foundation_support_email foundation_tenant foundation_terms_url "
    [ "$names" = "$expected_names" ] && ok "$app $type: config resources are the expected set" \
      || bad "$app $type: config resources are [$names], expected [$expected_names]"
    url=$(prop "$props" "api.$type.baseUrl"); case "$url" in */) ;; *) url="$url/" ;; esac
    for pair in "foundation_api_base_url=$url" "foundation_app_name=$name" "foundation_tenant=$(prop "$props" api.tenant)"; do
      grep -qxF "$pair" "$tmp/config" && ok "$app $type: $pair" \
        || bad "$app $type: expected $pair, APK has $(grep "^${pair%%=*}=" "$tmp/config" || echo 'nothing')"
    done

    unzip -p "$apk" 'classes*.dex' > "$tmp/dex"
    for other in $apps; do
      [ "$other" = "$app" ] && continue
      other_id=$(prop "apps/$other/app.properties" app.applicationId)
      if grep -aqF "$other_id" "$tmp/dex"; then
        bad "$app $type: dex contains $other's application ID $other_id"
      else
        ok "$app $type: no trace of $other ($other_id)"
      fi
    done

    icon=$("$aapt2" dump resources "$apk" \
      | awk '/drawable\/ic_launcher_foreground$/ { found = 1; next } found && /\(file\)/ { print $3; exit }')
    if [ -z "$icon" ]; then
      bad "$app $type: no drawable/ic_launcher_foreground"
    else
      echo "$(unzip -p "$apk" "$icon" | shasum | cut -d' ' -f1) $app" >> "$tmp/icons-$type"
    fi
  done

  same=$(sort "$tmp/icons-$type" | awk '{ if ($1 == prev) print prevapp " and " $2; prev = $1; prevapp = $2 }')
  if [ -n "$same" ]; then
    echo "$same" | while read -r line; do bad "$type: launcher foreground of $line is identical"; done
    failed=1
  else
    ok "$type: launcher foregrounds are distinct"
  fi
done

exit "$failed"
