#!/bin/sh
# Tests sync-openapi.sh and check-openapi.sh against a throwaway backend repo.
# Usage: scripts/test-openapi-scripts.sh   (exit 0 = all scenarios pass)
set -eu

root=$(cd "$(dirname "$0")/.." && pwd)
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
fail() { echo "FAIL $*"; exit 1; }
pass() { echo "OK   $*"; }

# A fake backend repo with the spec at tag t1.
backend="$tmp/backend"
mkdir -p "$backend/openapi"
git -C "$backend" init -q
printf '{"openapi":"3.1.0","info":{"title":"t","version":"1"}}\n' > "$backend/openapi/openapi.json"
git -C "$backend" add . && git -C "$backend" -c user.email=t@t -c user.name=t commit -qm t1 && git -C "$backend" tag t1

# The repo under test: just the two scripts.
repo="$tmp/repo"
mkdir -p "$repo/scripts"
cp "$root/scripts/sync-openapi.sh" "$root/scripts/check-openapi.sh" "$repo/scripts/"

(cd "$repo" && scripts/sync-openapi.sh t1 "$backend" > /dev/null) || fail "sync t1"
(cd "$repo" && scripts/check-openapi.sh > /dev/null) || fail "check after sync"
pass "sync then check"

cp "$repo/api/openapi.json" "$tmp/good.json"
printf ' ' >> "$repo/api/openapi.json"
if out=$(cd "$repo" && scripts/check-openapi.sh 2>&1); then fail "check accepted an edited spec"; fi
echo "$out" | grep -q "sync-openapi.sh" || fail "message does not name sync-openapi.sh: $out"
pass "an edited spec fails, naming the script"
cp "$tmp/good.json" "$repo/api/openapi.json"

cp "$repo/api/SOURCE" "$tmp/good-source"
grep -v '^commit=' "$tmp/good-source" > "$repo/api/SOURCE"
if (cd "$repo" && scripts/check-openapi.sh > /dev/null 2>&1); then fail "check accepted a SOURCE without commit"; fi
pass "a SOURCE missing a key fails"
cp "$tmp/good-source" "$repo/api/SOURCE"

before=$(cat "$repo/api/SOURCE")
if (cd "$repo" && scripts/sync-openapi.sh nope "$backend" > /dev/null 2>&1); then fail "sync accepted an unknown tag"; fi
[ "$before" = "$(cat "$repo/api/SOURCE")" ] || fail "an unknown tag changed api/SOURCE"
(cd "$repo" && scripts/check-openapi.sh > /dev/null) || fail "an unknown tag broke api/"
pass "an unknown tag fails and leaves api/ unchanged"
