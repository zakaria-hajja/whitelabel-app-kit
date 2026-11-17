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
| 3 | Android | Open Settings: no banner. Back to Home: the banner is back | partial (none under Settings; back to Home not checked) |
| 4 | Android | Ad-free: force-stop, `adb shell "run-as com.whitelabel.showcase.debug sh -c 'echo {\\\"untilEpochMillis\\\":4102444800000} > files/datastore/ads-free.json'"`, relaunch, sign in: no banner. Remove it with `adb shell run-as com.whitelabel.showcase.debug rm files/datastore/ads-free.json` | pass (emulator-5590) |
| 5 | Android | Consent failed: `adb shell "run-as com.whitelabel.showcase.debug sh -c 'mkdir -p files/debug && touch files/debug/fake-consent-failed'"`, relaunch, sign in: no banner. `adb shell run-as com.whitelabel.showcase.debug rm files/debug/fake-consent-failed` afterwards | pass (emulator-5590) |
| 6 | Android | With Firebase's real config and the AdMob app ID, in the EEA or with `consent-eea`: the form shows, and no banner loads before it is answered | |
| 7 | Android | Settings > privacy choices row (`PrivacyChoicesRow`) reopens the form while UMP requires one | |
| 8 | Android | Release build (`assembleRelease`): no ads code, no banner on any launch; `scripts/verify-apps.sh release` is green | partial (verify-apps only; Release launch not run) |
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

## Native (list item)

Showcase has no native placement, so run these on an app that declares one (Munch Debug after its
native-ads card, `ads { native(…) }` / `AdPlacement(…, kind: .native)`), in Debug on Google's native
test unit. Run each on Android and iOS, in light and dark, and once in French.

| # | Platform | Check | Result |
|---|---|---|---|
| N1 | Both | The "Ad" chip ("Annonce" in French) reads top-left, high contrast, at least 15 px | |
| N2 | Both | AdChoices sits in the card's top-right corner, uncovered; nothing of ours is drawn there | |
| N3 | Both | Only headline, media, icon and CTA open the ad; tapping the card's padding, the chip or the empty space does nothing. No swipe or long-press action | |
| N4 | Both | A video ad plays muted in the 120 media with the SDK's controls; an image is scaled to fit, never cropped | |
| N5 | Both | Long text truncates with an ellipsis (headline and body 2 lines, CTA 1 line), never overflows the card; the card's height stays 184 at the default text size | |
| N6 | Both | Font scale 1.3 / larger Dynamic Type: the card is taller (iOS 212 at 1.3; Android follows its nonlinear font scaling) and nothing is clipped, the CTA row included; it doesn't change size while on screen | |
| N7 | Both | A failed load (airplane mode, then open the list): no gap, no error, the list looks as without ads | |
| N8 | Both | Leave and come back within 60 s (detail and back, a tab flip): the same ad on the first frame, no reload, no jump, no insert animation | |
| N9 | Both | Come back after 60 s: the same ad again; the next entry after that shows a new one | |
| N10 | Both | Background the app over an hour (or move the clock forward 1 h), come back: the old ad is gone (not shown), a new one loads | |
| N11 | Both | Ad-free on (see check 4 / 11): the item disappears; it never shows on launch 1 or before consent. After the ad-free window (or a consent change that hid ads) ends, the list shows a new ad, never the one held before | |
| N12 | Both | The ad loads while its slot is on screen: it animates in, rows below move, not while a finger is down; with reduced motion it appears instantly (the app's insertion, ads.md "What the app still owns") | |
| N13 | Both | TalkBack / VoiceOver read "Ad" first, then the headline, advertiser, body and CTA; AdChoices is reachable. iOS: swiping on past the CTA reaches the advertiser icon (read as the advertiser's name) and then AdChoices, and double-tapping AdChoices opens Google's ad preferences. Android: listen for "Ad, <headline>" (the headline sits in an interop view) | |
| N14 | Both | **Native Ad Validator**: on test native ads in Debug, open its panel (Android: the validator's overlay on the ad; iOS: the same), it reports no errors or warnings. Screenshot it for the PR. Don't set `com.google.android.gms.ads.flag.NATIVE_AD_DEBUGGER_ENABLED=false` for QA | |

`npa=1` on native requests is pinned by unit tests (`AdsContributorTest`, `AdsServiceTests`), so it has no manual row.
