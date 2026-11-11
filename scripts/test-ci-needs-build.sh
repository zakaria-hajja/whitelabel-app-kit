#!/usr/bin/env bash
# Tests scripts/ci-needs-build.sh: which changes skip CI's Gradle steps (docs only) and which don't.
set -euo pipefail
cd "$(dirname "$0")/.."

failed=0
expect() { # expect <true|false> <description> <paths...>
  local want=$1 what=$2; shift 2
  local got
  got=$(printf '%s\n' "$@" | scripts/ci-needs-build.sh)
  if [ "$got" = "$want" ]; then echo "OK   $what"; else echo "FAIL $what: wanted $want, got $got"; failed=1; fi
}

expect false "a docs page"                    docs/testing.md
expect false "a plan under docs/superpowers"  docs/superpowers/plans/2026-11-11-x.md
expect false "the README and CLAUDE.md"       README.md CLAUDE.md
expect false "the PR template"                .github/pull_request_template.md
expect false "a shared run configuration"     .run/showcase.run.xml
expect true  "Kotlin source"                  core/network/src/main/kotlin/A.kt
expect true  "docs plus a Gradle property"    docs/testing.md gradle.properties
expect true  "the vendored contract"          api/openapi.json
expect true  "the CI workflow itself"         .github/workflows/ci.yml
expect true  "a script"                       scripts/check.sh
expect true  "no changes at all (be safe)"

exit "$failed"
