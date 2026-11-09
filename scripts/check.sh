#!/bin/sh
# Quality gate shared by the pre-push hook and CI: unit tests + Android lint for every
# module (Gradle `check`), plus the build-logic tests. Instrumented tests are not run here.
set -eu
cd "$(dirname "$0")/.."

if [ ! -x ./gradlew ]; then
  echo "check: no Gradle wrapper yet; nothing to check"
  exit 0
fi

./gradlew --quiet check
if [ -f build-logic/settings.gradle.kts ]; then
  ./gradlew --quiet -p build-logic check
fi
echo "check: unit tests and lint passed"
