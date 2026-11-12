# Ads: manual checklist (2026-11-12)

Checks that need a real device or simulator and a network, so no automated test does them. Run them
with showcase Debug (`ads.enabled=debug`, Google's test units). Android launch 1 and 2, the ad-free
window and the failed-consent switch were run on emulator-5590 while writing this; everything else
is blank until someone runs it. Fill the results in and commit them as `docs(ads): record the manual ads checks`.

Background: [ads.md](../../ads.md). Spec: [the design](../specs/2026-11-12-firebase-and-ads-kmp-design.md).

## Setup

- Android: `./gradlew -Pwhitelabel.mockApi=true :apps:showcase:installDebug`, then sign in with
  `alice@shared.test` / `Password123!`. The mock session doesn't survive a restart, so sign in again
  after each relaunch.
- iOS: `./gradlew :apps:showcase:shared:generateIosConfig`, `(cd ios/Showcase && xcodegen)`, open the
  project and run on "iPhone 18 Pro Max" with `-mockApi` in the scheme's arguments.
- Without Firebase's config files Debug runs the fake consent (default: obtained). With them, UMP is
  real; in the EEA the form shows first, accept it.
- `adb shell pm clear com.whitelabel.showcase.debug` / delete the app on iOS to be on launch 1 again.
- A test ad needs network. "Test Ad" is Google's test creative.

## Checks (result: pass / fail + note)

| # | Platform | Check | Result |
|---|---|---|---|
| 1 | Android | Launch 1 (fresh data): signed in on Home, no "Ad" label under Home | pass (emulator-5590) |
| 2 | Android | Force-stop, relaunch (launch 2), sign in: "Ad" and a "Test Ad" banner above the bottom bar, under Home, within about 15 s | pass (emulator-5590) |
| 3 | Android | Open Settings: no banner. Back to Home: the banner is back | pass (none under Settings) |
| 4 | Android | Ad-free: force-stop, `adb shell "run-as com.whitelabel.showcase.debug sh -c 'echo {\\\"untilEpochMillis\\\":4102444800000} > files/datastore/ads-free.json'"`, relaunch, sign in: no banner. Remove it with `adb shell run-as com.whitelabel.showcase.debug rm files/datastore/ads-free.json` | pass (emulator-5590) |
| 5 | Android | Consent failed: `adb shell "run-as com.whitelabel.showcase.debug sh -c 'mkdir -p files/debug && touch files/debug/fake-consent-failed'"`, relaunch, sign in: no banner. `adb shell run-as com.whitelabel.showcase.debug rm files/debug/fake-consent-failed` afterwards | pass (emulator-5590) |
| 6 | Android | With Firebase's real config and the AdMob app ID, in the EEA or with `consent-eea`: the form shows, and no banner loads before it is answered | |
| 7 | Android | Settings > privacy choices row (`PrivacyChoicesRow`) reopens the form while UMP requires one | |
| 8 | Android | Release build (`assembleRelease`): no ads code, no banner on any launch; `scripts/verify-apps.sh release` is green | pass (verify only) |
| 9 | iOS | Launch 1 (fresh install): no "Ad" under Home | |
| 10 | iOS | Launch 2: "Ad" and a "Test Ad" banner under Home, none under Settings | |
| 11 | iOS | Ad-free: write `{"untilEpochMillis":4102444800000}` to `datastore/ads-free.json` in the app container's Application Support (`xcrun simctl get_app_container booted com.whitelabel.showcase.debug data`), relaunch: no banner | |
| 12 | iOS | Consent failed: launch with `-fakeConsent failed` (needs Firebase on the fake): no banner | |
| 13 | iOS | Tracking on (`ads.ios.requestTracking=true` and a message in a scratch copy of showcase's properties): on launch 2 the consent form (if any) comes first, then the ATT prompt shows at the first banner, not at startup and not on launch 1; the banner appears after the answer | |
| 14 | iOS | Tracking off (default): no ATT prompt ever | |
| 15 | iOS | Settings > privacy choices row reopens the form | |
| 16 | iOS | Release build: starts without ads, no banner | |
| 17 | Both | Real units: put real units in `ads.android.banner.home` / `ads.ios.banner.home` with `ads.enabled=true` in a scratch copy, run Release on a device that is a registered test device: a banner loads. Never click real ads | |

Notes from the Android run: logcat showed the SDK starting when the first banner was allowed
(Home visible on launch 2), not at process start.
