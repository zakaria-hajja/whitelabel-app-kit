#!/bin/sh
# Verifies every app under apps/ (each directory with an app.properties), or one APK.
#
#   scripts/verify-apps.sh [--config-only] [debug|release ...]   (default: debug release)
#   scripts/verify-apps.sh --apk FILE --properties FILE --type debug|release
#
# The second form checks a single APK against its app.properties (package, label, version,
# deep-link scheme, config resources, launcher foreground present) and skips the checks that
# compare apps with each other. App repos with the foundation as a submodule use it in CI.
#
# Always: application IDs and deep-link schemes are unique across apps.
# Unless --config-only, for each app's built APK of each build type (aapt2 from the newest
# build-tools): package, label and version match app.properties; a release APK has no debug guest
# account; no other app's application ID
# appears in its dex; a release dex has no mock backend, no Firebase unless firebase.enabled=true and no AdMob SDK unless ads.enabled=true; its deep links use only its own scheme; its launcher foreground differs from every other app's; the generated
# foundation_* config resources the shell reads exist, match app.properties and nothing else.
# Prints one OK/FAIL line per check and exits non-zero if any check fails.
set -eu
# --apk and --properties are relative to where the script is called from (an app repo), so
# remember that before moving to the foundation root.
caller=$(pwd)
cd "$(dirname "$0")/.."
abs() { case "$1" in /*) echo "$1" ;; *) echo "$caller/$1" ;; esac; }

failed=0
ok() { echo "OK   $*"; }
bad() { echo "FAIL $*"; failed=1; }

# prop FILE KEY: the trimmed value of KEY in a properties file.
prop() { sed -n "s/^[[:space:]]*$2[[:space:]]*=[[:space:]]*//p" "$1" | head -n 1 | sed 's/[[:space:]]*$//'; }

find_aapt2() {
  sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}
  aapt2=$(ls -d "$sdk"/build-tools/*/aapt2 2>/dev/null | sort -V | tail -n 1)
  if [ -z "$aapt2" ]; then
    echo "FAIL aapt2 not found under $sdk/build-tools"
    exit 1
  fi
}

# ads_in_dex DEX: succeeds when the dex holds the AdMob SDK. R8 may rename package names, so also
# look for the init provider's class name, which the manifest keeps. The package
# com/google/android/gms/ads/identifier (play-services-ads-identifier, which Firebase Analytics
# pulls in) is not the AdMob SDK and is ignored.
ads_in_dex() {
  grep -aqF 'MobileAdsInitProvider' "$1" && return 0
  grep -aoE 'com/google/android/gms/ads/[A-Za-z]+' "$1" | grep -qv '^com/google/android/gms/ads/identifier$'
}

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

# verify_apk LABEL PROPS APK TYPE: the checks that need only this app's APK and app.properties.
verify_apk() {
  app=$1; props=$2; apk=$3; type=$4
  id=$(prop "$props" app.applicationId)
  [ "$type" = debug ] && id="$id.debug"
  badging=$("$aapt2" dump badging "$apk")

  # The debug guest sign-in's account is Debug-only: no foundation_debug_guest_* resource and no
  # seeded password (it lives in resources.arsc, not the dex) may reach a release APK.
  if [ "$type" = release ]; then
    if "$aapt2" dump resources "$apk" | grep -q foundation_debug_guest \
      || unzip -p "$apk" resources.arsc 'classes*.dex' | grep -aqF 'Password123!'; then
      bad "$app release: contains the debug guest account"
    else
      ok "$app release: no debug guest account"
    fi
  fi

  echo "$badging" | grep -q "^package: name='$id' " && ok "$app $type: package $id" \
    || bad "$app $type: package is not $id"
  name=$(prop "$props" app.name)
  echo "$badging" | grep -qF "application-label:'$name'" && ok "$app $type: label '$name'" \
    || bad "$app $type: label is not '$name'"
  code=$(prop "$props" app.versionCode); version=$(prop "$props" app.versionName)
  echo "$badging" | grep -q "versionCode='$code' versionName='$version'" && ok "$app $type: version $code ($version)" \
    || bad "$app $type: version is not $code ($version)"
  # Deep links (reset password, catalog items) open the app only through its own scheme. A library's
  # <queries> (the AdMob SDK's https/sms intents: package visibility, not links into the app) is skipped.
  scheme=$(prop "$props" app.deepLinkScheme)
  schemes=$("$aapt2" dump xmltree --file AndroidManifest.xml "$apk" \
    | awk '{ match($0, /^ */); d = RLENGTH }
           skip && d > depth { next }
           { skip = 0 }
           /E: queries / { skip = 1; depth = d; next }
           { print }' \
    | sed -n 's/.*android:scheme([^)]*)="\([^"]*\)".*/\1/p' | sort -u | tr '\n' ' ')
  [ "$schemes" = "$scheme " ] && ok "$app $type: deep links use scheme '$scheme'" \
    || bad "$app $type: deep-link schemes are '$schemes', expected only '$scheme'"

  # Config the shell reads at runtime: must match app.properties, and the generated names must be
  # exactly the ones the shell declares (a renamed field would otherwise read as blank).
  "$aapt2" dump resources "$apk" \
    | awk '/string\/foundation_/ { sub(/.*string\//, ""); name = $1; next }
           name != "" { v = $0; sub(/^[^"]*"/, "", v); sub(/"[^"]*$/, "", v); print name "=" v; name = "" }' \
    | sort > "$tmp/config"
  names=$(cut -d= -f1 "$tmp/config" | LC_ALL=C sort | tr '\n' ' ')
  # Debug builds also carry the debug guest sign-in's account (from :core:shell's debug resources).
  guest_names=""
  [ "$type" = debug ] && guest_names="foundation_debug_guest_email foundation_debug_guest_password "
  expected_names="foundation_ads foundation_ads_declared foundation_ads_units foundation_api_base_url foundation_app_name ${guest_names}foundation_deep_link_scheme foundation_default_features foundation_environment foundation_firebase foundation_firebase_declared foundation_privacy_policy_url foundation_support_email foundation_tenant foundation_terms_url "
  [ "$names" = "$expected_names" ] && ok "$app $type: config resources are the expected set" \
    || bad "$app $type: config resources are [$names], expected [$expected_names]"
  # No api.<type>.baseUrl means an app without a backend: the generated URL is empty.
  url=$(prop "$props" "api.$type.baseUrl"); case "$url" in ""|*/) ;; *) url="$url/" ;; esac
  # The mode itself depends on build type and google-services.json, so only "declared" is compared.
  case "$(prop "$props" firebase.enabled)" in true|debug) declared=true ;; *) declared=false ;; esac
  # The ads mode per build type: true is on in both, debug only in debug builds.
  case "$(prop "$props" ads.enabled)" in
    true) ads=on ;;
    debug) if [ "$type" = debug ]; then ads=on; else ads=off; fi ;;
    *) ads=off ;;
  esac
  case "$(prop "$props" ads.enabled)" in true|debug) ads_declared=true ;; *) ads_declared=false ;; esac
  for pair in "foundation_firebase_declared=$declared" "foundation_ads=$ads" "foundation_ads_declared=$ads_declared" "foundation_api_base_url=$url" "foundation_app_name=$name" "foundation_tenant=$(prop "$props" api.tenant)"; do
    grep -qxF "$pair" "$tmp/config" && ok "$app $type: $pair" \
      || bad "$app $type: expected $pair, APK has $(grep "^${pair%%=*}=" "$tmp/config" || echo 'nothing')"
  done

  icon=$("$aapt2" dump resources "$apk" \
    | awk '/drawable\/ic_launcher_foreground$/ { found = 1; next } found && /\(file\)/ { print $3; exit }')
  if [ -z "$icon" ]; then
    bad "$app $type: no drawable/ic_launcher_foreground"
  fi
}

if [ "${1:-}" = "--apk" ]; then
  apk=""; props=""; type=""
  while [ $# -gt 0 ]; do
    case "$1" in
      --apk) apk=$(abs "$2"); shift 2 ;;
      --properties) props=$(abs "$2"); shift 2 ;;
      --type) type=$2; shift 2 ;;
      *) echo "FAIL unknown argument $1"; exit 2 ;;
    esac
  done
  if [ ! -f "$apk" ] || [ ! -f "$props" ] || { [ "$type" != debug ] && [ "$type" != release ]; }; then
    echo "FAIL usage: scripts/verify-apps.sh --apk FILE --properties FILE --type debug|release"
    exit 2
  fi
  find_aapt2
  verify_apk "$(basename "$(dirname "$props")")" "$props" "$apk" "$type"
  exit "$failed"
fi

config_only=false
if [ "${1:-}" = "--config-only" ]; then config_only=true; shift; fi
types=${*:-debug release}

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

find_aapt2

for type in $types; do
  : > "$tmp/icons-$type"
  for app in $apps; do
    props="apps/$app/app.properties"
    apk=$(ls -t "apps/$app/build/outputs/apk/$type"/*.apk 2>/dev/null | head -n 1 || true)
    if [ -z "$apk" ]; then
      bad "$app $type: no APK built (run ./gradlew :apps:$app:assemble$(echo "$type" | cut -c1 | tr a-z A-Z)$(echo "$type" | cut -c2-))"
      continue
    fi
    verify_apk "$app" "$props" "$apk" "$type"

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

    # The in-memory backend is debug-only; R8 may rename its classes, so also look for its token prefix.
    if [ "$type" = release ]; then
      if grep -aqE 'com/whitelabel/core/mockapi|fake-refresh-' "$tmp/dex"; then
        bad "$app release: dex contains the mock backend (:core:mockapi)"
      else
        ok "$app release: no mock backend"
      fi
    fi

    # Firebase and the consent SDK ship only in an app that declares firebase.enabled=true. R8 may
    # rename or drop package names, so also look for strings the SDKs embed that it can't change:
    # the FirebaseInitProvider class name and the Firebase Installations host, and the UMP
    # consent-form host.
    if [ "$type" = release ]; then
      if grep -aqE 'com/google/firebase|com/google/android/ump|FirebaseInitProvider|firebaseinstallations\.googleapis\.com|fundingchoicesmessages\.google\.com' "$tmp/dex" \
        && [ "$(prop "$props" firebase.enabled)" != true ]; then
        bad "$app release: dex contains Firebase (firebase.enabled is not true)"
      else
        ok "$app release: no Firebase beyond firebase.enabled"
      fi
    fi

    # The AdMob SDK ships only in an app that declares ads.enabled=true (debug mode is debug only).
    if [ "$type" = release ]; then
      if ads_in_dex "$tmp/dex" && [ "$(prop "$props" ads.enabled)" != true ]; then
        bad "$app release: dex contains the AdMob SDK (ads.enabled is not true)"
      else
        ok "$app release: no AdMob SDK beyond ads.enabled"
      fi
    fi

    if [ -n "$icon" ]; then
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
