#!/bin/sh
# The WhitelabelAdsApp template's build phase: adds the ads keys to the built app's Info.plist, so an
# app's own Info.plist needs only GADApplicationIdentifier (and WLAdsDeclared, which every app maps).
# It runs after Xcode processed the Info.plist and before signing. Argument: the built Info.plist
# (default: $TARGET_BUILD_DIR/$INFOPLIST_PATH).
# - Always: the settings PlatformFactory and AdsService read, from the generated xcconfig: WLAds
#   (WL_ADS), WLAdsUnits (WL_ADS_UNITS), WLAdsDeclared (WL_ADS_DECLARED), WLAdsTracking (WL_ADS_TRACKING).
# - WL_ADS on: Google's SKAdNetworkItems (the plist fragment at the end of this script). An app that
#   lists its own SKAdNetworkItems keeps its list.
# - WL_ADS on and WL_ADS_TRACKING yes: NSUserTrackingUsageDescription = WL_ADS_TRACKING_MESSAGE, the
#   text of the ATT prompt (iOS stops an app that asks without it).
# With ads off for the build (ads.enabled=debug in Release) it adds nothing else.
# XcodeGen copies this script into the build phase, so it carries its fragment instead of reading a
# file beside it (the phase can't tell where the foundation is).
set -eu
plist="${1:-${TARGET_BUILD_DIR:-}/${INFOPLIST_PATH:-}}"
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
skadnetwork_items() {
  cat <<'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<!-- Google's SKAdNetwork identifiers for AdMob, as listed at
     https://developers.google.com/admob/ios/quick-start#update_your_infoplist_file (also
     https://developers.google.com/admob/ios/3p-skadnetworks), copied 2026-11-12 (page last
     updated 2026-11-12; 50 entries, Google's cstr6suwn9 first). The WhitelabelAdsApp template merges
     this into the built app's Info.plist when WL_ADS is on. Refresh it from that page. -->
<plist version="1.0">
<dict>
	<key>SKAdNetworkItems</key>
	<array>
		<dict><key>SKAdNetworkIdentifier</key><string>cstr6suwn9.skadnetwork</string></dict>
		<dict><key>SKAdNetworkIdentifier</key><string>4fzdc2evr5.skadnetwork</string></dict>
		<dict><key>SKAdNetworkIdentifier</key><string>2fnua5tdw4.skadnetwork</string></dict>
		<dict><key>SKAdNetworkIdentifier</key><string>ydx93a7ass.skadnetwork</string></dict>
		<dict><key>SKAdNetworkIdentifier</key><string>p78axxw29g.skadnetwork</string></dict>
		<dict><key>SKAdNetworkIdentifier</key><string>v72qych5uu.skadnetwork</string></dict>
		<dict><key>SKAdNetworkIdentifier</key><string>ludvb6z3bs.skadnetwork</string></dict>
		<dict><key>SKAdNetworkIdentifier</key><string>cp8zw746q7.skadnetwork</string></dict>
		<dict><key>SKAdNetworkIdentifier</key><string>3sh42y64q3.skadnetwork</string></dict>
		<dict><key>SKAdNetworkIdentifier</key><string>c6k4g5qg8m.skadnetwork</string></dict>
		<dict><key>SKAdNetworkIdentifier</key><string>s39g8k73mm.skadnetwork</string></dict>
		<dict><key>SKAdNetworkIdentifier</key><string>wg4vff78zm.skadnetwork</string></dict>
		<dict><key>SKAdNetworkIdentifier</key><string>3qy4746246.skadnetwork</string></dict>
		<dict><key>SKAdNetworkIdentifier</key><string>f38h382jlk.skadnetwork</string></dict>
		<dict><key>SKAdNetworkIdentifier</key><string>hs6bdukanm.skadnetwork</string></dict>
		<dict><key>SKAdNetworkIdentifier</key><string>mlmmfzh3r3.skadnetwork</string></dict>
		<dict><key>SKAdNetworkIdentifier</key><string>v4nxqhlyqp.skadnetwork</string></dict>
		<dict><key>SKAdNetworkIdentifier</key><string>wzmmz9fp6w.skadnetwork</string></dict>
		<dict><key>SKAdNetworkIdentifier</key><string>su67r6k2v3.skadnetwork</string></dict>
		<dict><key>SKAdNetworkIdentifier</key><string>yclnxrl5pm.skadnetwork</string></dict>
		<dict><key>SKAdNetworkIdentifier</key><string>t38b2kh725.skadnetwork</string></dict>
		<dict><key>SKAdNetworkIdentifier</key><string>7ug5zh24hu.skadnetwork</string></dict>
		<dict><key>SKAdNetworkIdentifier</key><string>gta9lk7p23.skadnetwork</string></dict>
		<dict><key>SKAdNetworkIdentifier</key><string>vutu7akeur.skadnetwork</string></dict>
		<dict><key>SKAdNetworkIdentifier</key><string>y5ghdn5j9k.skadnetwork</string></dict>
		<dict><key>SKAdNetworkIdentifier</key><string>v9wttpbfk9.skadnetwork</string></dict>
		<dict><key>SKAdNetworkIdentifier</key><string>n38lu8286q.skadnetwork</string></dict>
		<dict><key>SKAdNetworkIdentifier</key><string>47vhws6wlr.skadnetwork</string></dict>
		<dict><key>SKAdNetworkIdentifier</key><string>kbd757ywx3.skadnetwork</string></dict>
		<dict><key>SKAdNetworkIdentifier</key><string>9t245vhmpl.skadnetwork</string></dict>
		<dict><key>SKAdNetworkIdentifier</key><string>a2p9lx4jpn.skadnetwork</string></dict>
		<dict><key>SKAdNetworkIdentifier</key><string>22mmun2rn5.skadnetwork</string></dict>
		<dict><key>SKAdNetworkIdentifier</key><string>44jx6755aq.skadnetwork</string></dict>
		<dict><key>SKAdNetworkIdentifier</key><string>k674qkevps.skadnetwork</string></dict>
		<dict><key>SKAdNetworkIdentifier</key><string>4468km3ulz.skadnetwork</string></dict>
		<dict><key>SKAdNetworkIdentifier</key><string>2u9pt9hc89.skadnetwork</string></dict>
		<dict><key>SKAdNetworkIdentifier</key><string>8s468mfl3y.skadnetwork</string></dict>
		<dict><key>SKAdNetworkIdentifier</key><string>klf5c3l5u5.skadnetwork</string></dict>
		<dict><key>SKAdNetworkIdentifier</key><string>ppxm28t8ap.skadnetwork</string></dict>
		<dict><key>SKAdNetworkIdentifier</key><string>kbmxgpxpgc.skadnetwork</string></dict>
		<dict><key>SKAdNetworkIdentifier</key><string>uw77j35x4d.skadnetwork</string></dict>
		<dict><key>SKAdNetworkIdentifier</key><string>578prtvx9j.skadnetwork</string></dict>
		<dict><key>SKAdNetworkIdentifier</key><string>4dzt52r2t5.skadnetwork</string></dict>
		<dict><key>SKAdNetworkIdentifier</key><string>tl55sbb4fm.skadnetwork</string></dict>
		<dict><key>SKAdNetworkIdentifier</key><string>c3frkrj4fj.skadnetwork</string></dict>
		<dict><key>SKAdNetworkIdentifier</key><string>e5fvkxwrpn.skadnetwork</string></dict>
		<dict><key>SKAdNetworkIdentifier</key><string>8c4e2ghe7u.skadnetwork</string></dict>
		<dict><key>SKAdNetworkIdentifier</key><string>3rd42ekr43.skadnetwork</string></dict>
		<dict><key>SKAdNetworkIdentifier</key><string>97r2b46745.skadnetwork</string></dict>
		<dict><key>SKAdNetworkIdentifier</key><string>3qcr597p9d.skadnetwork</string></dict>
	</array>
</dict>
</plist>
PLIST
}

# PlistBuddy splits its command on spaces: merge from a copy at a path without any.
fragment="$(mktemp)"
trap 'rm -f "$fragment"' EXIT
skadnetwork_items > "$fragment"
/usr/libexec/PlistBuddy -c "Merge $fragment" "$plist" >/dev/null
if [ "${WL_ADS_TRACKING:-no}" = "yes" ]; then
  if [ -z "${WL_ADS_TRACKING_MESSAGE:-}" ]; then
    echo "error: merge-info-plist: WL_ADS_TRACKING is yes but WL_ADS_TRACKING_MESSAGE is empty (ads.ios.trackingMessage)"
    exit 1
  fi
  plutil -replace NSUserTrackingUsageDescription -string "$WL_ADS_TRACKING_MESSAGE" "$plist"
fi
