#!/bin/sh
# The iOS gate (macOS with Xcode): WhitelabelKit's Swift tests (and showcase's UI tests, as they
# are added) on a simulator, and a Release build that must not contain the fake backend. The KMP
# modules' iOS tests run in check.sh.
# GitHub CI stays Android-only; the pre-push hook runs this on macOS.
set -eu
cd "$(dirname "$0")/.."

if [ "$(uname)" != "Darwin" ]; then
  echo "check-ios: skipped (not macOS)"
  exit 0
fi
if ! xcrun --sdk iphonesimulator --show-sdk-path >/dev/null 2>&1; then
  echo "check-ios: skipped: no Xcode iOS SDK (install Xcode, then: sudo xcode-select -s /Applications/Xcode.app/Contents/Developer)"
  exit 0
fi
if ! command -v xcodegen >/dev/null 2>&1; then
  echo "check-ios: skipped: xcodegen is missing (brew install xcodegen)"
  exit 0
fi

SIMULATOR="${WL_IOS_SIMULATOR:-iPhone 18 Pro}"
DERIVED=ios/Showcase/build/dd

./gradlew --quiet :apps:showcase:shared:generateIosConfig
(cd ios/Showcase && xcodegen --quiet)

echo "check-ios: Swift tests on $SIMULATOR"
xcodebuild -quiet -project ios/Showcase/Showcase.xcodeproj -scheme Showcase \
  -destination "platform=iOS Simulator,name=$SIMULATOR" -derivedDataPath "$DERIVED" test

echo "check-ios: Release build"
xcodebuild -quiet -project ios/Showcase/Showcase.xcodeproj -scheme Showcase -configuration Release \
  -destination "platform=iOS Simulator,name=$SIMULATOR" -derivedDataPath "$DERIVED" build
RELEASE_PRODUCTS="${WL_RELEASE_PRODUCTS:-$DERIVED/Build/Products/Release-iphonesimulator}"
binary=$(find "$RELEASE_PRODUCTS" -maxdepth 2 -path "*.app/*" -type f -perm -u+x 2>/dev/null | head -1)
if [ -z "$binary" ]; then
  echo "check-ios: FAIL: no Release app binary under $RELEASE_PRODUCTS, so the fake-backend check can't run"
  exit 1
fi
symbols=$(mktemp)
nm "$binary" > "$symbols"
if grep -q FakeBackend "$symbols"; then
  echo "check-ios: FAIL: the Release build contains the fake backend ($binary)"
  exit 1
fi

echo "check-ios: passed"
