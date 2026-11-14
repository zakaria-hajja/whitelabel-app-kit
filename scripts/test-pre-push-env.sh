#!/usr/bin/env bash
# Tests .githooks/pre-push: the check scripts it runs must not inherit git's repo-location variables.
# In a linked worktree git hands hooks an absolute GIT_DIR (<repo>/.git/worktrees/<name>); left in
# place it leaks into xcodebuild's SwiftPM, whose own git commands then act on this repo instead of
# the package checkout. The test pushes from a linked worktree of a throwaway repo whose check
# scripts are stubs that record their GIT_* environment.
set -euo pipefail
cd "$(dirname "$0")/.."
hook="$PWD/.githooks/pre-push"

# This test may itself run under a hook or with GIT_DIR set: start clean.
# shellcheck disable=SC2046
unset $(git rev-parse --local-env-vars)

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
export GIT_CONFIG_NOSYSTEM=1 HOME="$tmp/home" GIT_AUTHOR_NAME=t GIT_AUTHOR_EMAIL=t@t \
  GIT_COMMITTER_NAME=t GIT_COMMITTER_EMAIL=t@t
unset GIT_CONFIG_GLOBAL XDG_CONFIG_HOME
mkdir -p "$HOME" "$tmp/bin"

# Pretend to be macOS so the iOS gate runs too, on Linux CI as well.
printf '#!/bin/sh\necho Darwin\n' > "$tmp/bin/uname"
chmod +x "$tmp/bin/uname"
export PATH="$tmp/bin:$PATH"

git init -q --bare "$tmp/remote.git"
git init -q -b main "$tmp/repo"
cd "$tmp/repo"
mkdir -p .githooks scripts
cp "$hook" .githooks/pre-push
for script in check check-ios; do
  printf '#!/bin/sh\nenv | grep "^GIT_" > "%s/%s.env" || true\n' "$tmp" "$script" > "scripts/$script.sh"
done
git add -A
git commit -q -m init
git remote add origin "$tmp/remote.git"
git push -q origin main
git fetch -q origin
git config core.hooksPath .githooks

git worktree add -q -b fix/x "$tmp/wt"
cd "$tmp/wt"
mkdir ios && echo change > ios/file.swift
git add -A
git commit -q -m change
git push -q origin fix/x

failed=0
location_vars=$(git rev-parse --local-env-vars)
for script in check check-ios; do
  if [ ! -f "$tmp/$script.env" ]; then
    echo "FAIL $script.sh was not run by the hook"; failed=1; continue
  fi
  leaked=""
  for var in $location_vars; do
    grep -q "^$var=" "$tmp/$script.env" && leaked="$leaked $var"
  done
  if [ -z "$leaked" ]; then
    echo "OK   $script.sh runs without git's repo-location variables"
  else
    echo "FAIL $script.sh inherited:$leaked"; failed=1
  fi
done

# A push that changes only the hook must run the iOS gate, since the hook decides how it runs.
rm -f "$tmp/check-ios.env"
echo '# changed' >> .githooks/pre-push
git commit -q -am "change the hook"
git push -q origin fix/x
if [ -f "$tmp/check-ios.env" ]; then
  echo "OK   a change to the hook runs check-ios.sh"
else
  echo "FAIL a change to the hook did not run check-ios.sh"; failed=1
fi

exit "$failed"
