#!/bin/sh
# Fails if api/openapi.json is not exactly the file scripts/sync-openapi.sh recorded in api/SOURCE.
set -eu
cd "$(dirname "$0")/.."

fail() {
  echo "check-openapi: $1" >&2
  echo "check-openapi: api/openapi.json does not match api/SOURCE: update it with scripts/sync-openapi.sh <tag>, never by hand" >&2
  exit 1
}

[ -f api/openapi.json ] || fail "api/openapi.json is missing"
[ -f api/SOURCE ] || fail "api/SOURCE is missing"
value() { sed -n "s/^$1=//p" api/SOURCE | head -n 1; }
for key in tag commit sha256; do
  [ -n "$(value "$key")" ] || fail "api/SOURCE has no $key"
done

sha256() { if command -v sha256sum > /dev/null; then sha256sum "$1"; else shasum -a 256 "$1"; fi | cut -d' ' -f1; }
[ "$(sha256 api/openapi.json)" = "$(value sha256)" ] || fail "api/openapi.json changed since $(value tag)"
echo "check-openapi: api/openapi.json is $(value tag) ($(value commit))"
