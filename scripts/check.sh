#!/bin/sh
# Quality gate shared by the pre-push hook and CI: unit tests + Android lint for every
# module (Gradle `check`), plus the build-logic tests. Instrumented tests are not run here.
# On macOS with Xcode, `check` also runs the KMP modules' iOS simulator tests.
set -eu
cd "$(dirname "$0")/.."

if [ ! -x ./gradlew ]; then
  echo "check: no Gradle wrapper yet; nothing to check"
  exit 0
fi

# A Mac without Xcode compiles for iOS but can't link or run the simulator tests: skip those, loudly.
ios_flag=""
if [ "$(uname)" = "Darwin" ] && ! xcrun --sdk iphonesimulator --show-sdk-path >/dev/null 2>&1; then
  echo "check: WARNING: no Xcode iOS SDK, so iOS tests are skipped (iOS code still compiles)."
  echo "check:          Install Xcode and run: sudo xcode-select -s /Applications/Xcode.app/Contents/Developer"
  ios_flag="-Pwhitelabel.skipIosTests=true"
fi

./gradlew --quiet check $ios_flag
if [ -f build-logic/settings.gradle.kts ]; then
  ./gradlew --quiet -p build-logic check
fi
echo "check: unit tests and lint passed"
