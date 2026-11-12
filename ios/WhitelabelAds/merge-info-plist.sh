#!/bin/sh
# The WhitelabelAdsApp template's build phase: adds the ads keys to the built app's Info.plist, so an
# app's own Info.plist needs only GADApplicationIdentifier (and WLAdsDeclared, which every app maps).
# It runs after Xcode processed the Info.plist and before signing. Argument: the built Info.plist
# (default: $TARGET_BUILD_DIR/$INFOPLIST_PATH).
# - Always: the settings PlatformFactory and AdsService read, from the generated xcconfig: WLAds
#   (WL_ADS), WLAdsUnits (WL_ADS_UNITS), WLAdsDeclared (WL_ADS_DECLARED), WLAdsTracking (WL_ADS_TRACKING).
# - WL_ADS on: Google's SKAdNetworkItems (Resources/SKAdNetworkItems.plist). An app that lists its
#   own SKAdNetworkItems keeps its list.
# - WL_ADS on and WL_ADS_TRACKING yes: NSUserTrackingUsageDescription = WL_ADS_TRACKING_MESSAGE, the
#   text of the ATT prompt (iOS stops an app that asks without it).
# With ads off for the build (ads.enabled=debug in Release) it adds nothing.
set -eu
plist="${1:-${TARGET_BUILD_DIR:-}/${INFOPLIST_PATH:-}}"
here="$(cd "$(dirname "$0")" && pwd)"
if [ ! -f "$plist" ]; then
  echo "error: merge-info-plist: no Info.plist at $plist"
  exit 1
fi
plutil -replace WLAds -string "${WL_ADS:-off}" "$plist"
plutil -replace WLAdsUnits -string "${WL_ADS_UNITS:-}" "$plist"
plutil -replace WLAdsDeclared -string "${WL_ADS_DECLARED:-false}" "$plist"
plutil -replace WLAdsTracking -string "${WL_ADS_TRACKING:-no}" "$plist"
if [ "${WL_ADS:-off}" != "on" ]; then
  exit 0
fi
# PlistBuddy splits its command on spaces: merge from a copy at a path without any.
fragment="$(mktemp)"
trap 'rm -f "$fragment"' EXIT
cp "$here/Resources/SKAdNetworkItems.plist" "$fragment"
/usr/libexec/PlistBuddy -c "Merge $fragment" "$plist" >/dev/null
if [ "${WL_ADS_TRACKING:-no}" = "yes" ]; then
  if [ -z "${WL_ADS_TRACKING_MESSAGE:-}" ]; then
    echo "error: merge-info-plist: WL_ADS_TRACKING is yes but WL_ADS_TRACKING_MESSAGE is empty (ads.ios.trackingMessage)"
    exit 1
  fi
  plutil -replace NSUserTrackingUsageDescription -string "$WL_ADS_TRACKING_MESSAGE" "$plist"
fi
