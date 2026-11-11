#!/usr/bin/env bash
# Prints "true" when a change needs CI's Gradle steps, "false" when it touches only docs.
#
#   scripts/ci-needs-build.sh BASE HEAD   # the files changed between BASE and HEAD
#   ... | scripts/ci-needs-build.sh       # or a list of paths on stdin
#
# Docs: *.md anywhere, docs/**, the PR template, and shared IDE run configurations (.run/).
# Anything else, or no changes at all, needs a build.
set -euo pipefail

if [ $# -eq 2 ]; then
  files=$(git diff --name-only "$1"..."$2")
else
  files=$(cat)
fi

needs=false
[ -z "$files" ] && needs=true
while IFS= read -r f; do
  [ -z "$f" ] && continue
  case "$f" in
    *.md | docs/* | .github/pull_request_template.md | .run/*) ;;
    *) needs=true ;;
  esac
done <<< "$files"
echo "$needs"
