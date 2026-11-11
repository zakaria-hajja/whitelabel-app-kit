#!/bin/sh
# Vendors the backend contract: api/openapi.json from the backend repo at a tag, plus api/SOURCE
# (tag, commit, sha256) so scripts/check-openapi.sh can tell if it was edited by hand.
# Usage: scripts/sync-openapi.sh <tag> [backend-dir]     (backend-dir defaults to ../backend-api, next to this repo)
set -eu

tag=${1:?usage: scripts/sync-openapi.sh <tag> [backend-dir]}
# A backend dir given on the command line is relative to where the script is run; then work in this
# script's repo (e.g. an app repo's foundation/ submodule), never in the current directory.
if [ -n "${2:-}" ]; then backend=$(cd "$2" && pwd); fi
cd "$(dirname "$0")/.."
backend=${backend:-../backend-api}

commit=$(git -C "$backend" rev-parse --verify --quiet "$tag^{commit}") || {
  echo "sync-openapi: tag '$tag' not found in $backend" >&2
  exit 1
}
tmp=$(mktemp)
trap 'rm -f "$tmp"' EXIT
git -C "$backend" show "$commit:openapi/openapi.json" > "$tmp"

sha256() { if command -v sha256sum > /dev/null; then sha256sum "$1"; else shasum -a 256 "$1"; fi | cut -d' ' -f1; }

mkdir -p api
cp "$tmp" api/openapi.json
printf 'tag=%s\ncommit=%s\nsha256=%s\n' "$tag" "$commit" "$(sha256 api/openapi.json)" > api/SOURCE
echo "sync-openapi: api/openapi.json is now $tag ($commit)"
