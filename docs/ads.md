# Ads

An app shows AdMob banners and native ads without writing SDK code: one config key, a placement per
ad and one line per platform. Banners, and one native list item per placement. The foundation owns the rules (consent, first launch, ad-free
windows), so an app can't get them wrong. Design:
[2026-11-12-firebase-and-ads-kmp-design.md](superpowers/specs/2026-11-12-firebase-and-ads-kmp-design.md).
Consent (Google's UMP) is shared with Firebase: see [firebase.md](firebase.md).

An app without ads gets nothing: no SDK, no Gradle plugin, no Swift package on the app target, and no
Info.plist keys beyond the empty placeholders. (The shared shell still links the SDK-free ads rules
modules, which do nothing without `ads { }`.)

## Modules

| Module | Platform | What |
|---|---|---|
| `:core:ads:shared` | KMP | `AdGate` (the rules), `AdsController` (one state per placement, banner or native), `NativeAdRotation` (when a native ad is requested, shown, kept and destroyed), `LaunchCounter`, `AdFree`, `TrackingPort`, and the wiring and placement checks both platforms share |
| `:core:ads` | Android | `AdBanner(placement)`, `rememberNativeAd(placement)` + `NativeAdItem(slot)`, the `ads { placement(…); native(…) }` DSL, the `BannerHost` and `NativeAdHost` interfaces. No Google SDK |
| `:core:ads:google` | Android | Google Mobile Ads: the `AdView` behind `BannerHost`, the `AdLoader` and fixed native template behind `NativeAdHost`, started on the first ad |
| `ios/WhitelabelAds` | iOS | Swift target `WhitelabelAds`: `AdsService`, Google's banner view and native template, the ATT prompt. The XcodeGen template `WhitelabelAdsApp` links it and writes the ads keys into the built Info.plist |

The kit's `AdBanner(placement:)` and `NativeAdItem(slot:)` (WhitelabelKit) and the shell's tab slot
draw the ads; none of them imports the ads SDK.

## Turning it on

**1. `app.properties`:**

| `ads.enabled` | Effect |
|---|---|
| `false` (default) | No ads: no SDK in the Android build; on iOS nothing starts |
| `true` | Every build type. Both AdMob app IDs and a unit for every placement are required and checked for format |
| `debug` | Debug builds only, always on Google's test units, so no unit IDs are needed. A Release build has no SDK on Android and never starts it on iOS |

| Key | Format | Rule |
|---|---|---|
| `ads.android.appId`, `ads.ios.appId` | `ca-app-pub-<16 digits>~<10 digits>` | Same keys Firebase uses ([firebase.md](firebase.md)). Required with `ads.enabled=true` |
| `ads.android.banner.<placement>` | `ca-app-pub-<16 digits>/<10 digits>` | The real AdMob banner unit for that placement on Android |
| `ads.ios.banner.<placement>` | same | The same on iOS |
| `ads.android.native.<placement>`, `ads.ios.native.<placement>` | same | The real AdMob **Native advanced** unit for a native placement (see "Native ads") |
| `ads.nonPersonalized` | `false` (default) or `true` | Every ad request asks for a non-personalized ad, on both platforms (see "Non-personalized ads") |
| `ads.ios.requestTracking` | `false` (default) or `true` | Ask for App Tracking Transparency (see below) |
| `ads.ios.trackingMessage` | text | The ATT prompt's explanation. Required with `requestTracking=true`; must not contain `$(` or `${` |

`<placement>` is a name (`^[A-Za-z][A-Za-z0-9_-]*$`) that the app also declares in code, unique across
banners and native ads (the same name for both fails, and so does a native placement's unit under the
`banner.` key). A unit with
no matching placement, or a placement with no unit in a build that isn't Debug and has ads on (so
`true` mode in Release), fails the build or the launch with a message naming the key. Debug always
gets the test unit. Showcase uses `ads.enabled=debug`, so its
properties hold no unit IDs.

**Where the real units go:** AdMob console > Apps > the app > Ad units > Add ad unit > Banner (or
**Native advanced** for a native placement). Copy its ID (`ca-app-pub-...~` is the app, `.../` with a
slash is the unit) into `ads.android.banner.home` / `ads.ios.banner.home` (or
`ads.android.native.<placement>` / `ads.ios.native.<placement>`). Units are public identifiers, like the app IDs, so
they live in `app.properties`.

**Test units.** Debug always uses Google's public test units, whatever the properties say, so a
developer build never serves or counts a real ad:

| | App ID | Adaptive banner | Native advanced |
|---|---|---|---|
| Android | `ca-app-pub-3940256099942544~3347511713` | `ca-app-pub-3940256099942544/9214589741` | `ca-app-pub-3940256099942544/2247696110` |
| iOS | `ca-app-pub-3940256099942544~1458002511` | `ca-app-pub-3940256099942544/2435281174` | `ca-app-pub-3940256099942544/3986624511` |

(From developers.google.com/admob/android/test-ads and …/ios/test-ads; re-check them when adding a
format. Each ID has two copies, the runtime `AdsPlatform` and the build-time `Platform`, pinned by tests.)

When the app has its own app ID, Debug keeps it (UMP finds the app's consent message through it) and
only swaps the units. Test ads need network.

**2. Android, in the app's definition:**

```kotlin
fun globexDefinition(): AppDefinition = appDefinition {
    ads { placement("home", underTab = HomeRoute) }   // a banner under the Home tab
    // ...
}
```

`ads { }` can be called more than once. The banner sits above the bottom bar and shows only while
that tab's root screen is on top. `ads { }` without `ads.enabled`, or the reverse, fails at startup
with `ads { } needs ads.enabled=true or debug in app.properties` /
`ads.enabled is set but the app definition doesn't call ads { }`.

**3. iOS, in the app's `@main`:**

```swift
import WhitelabelAds

RootView(definition: AppDefinition(style: .material, startTab: HomeRoutes.shared.ROOT,
                                   services: [FirebaseService(), AdsService()],
                                   ads: [AdPlacement(name: "home", underTab: HomeRoutes.shared.ROOT)]) { … })
```

- Include `WhitelabelAds.yml` in the app's `project.yml` and put the template on the app target
  (copy `ios/Showcase/project.yml`): `templates: [WhitelabelFirebaseApp, WhitelabelAdsApp]`, or
  `[WhitelabelConsentApp, WhitelabelAdsApp]` without Firebase (XcodeGen takes the consent target from
  one template only). `-ObjC` goes in `OTHER_LDFLAGS`.
- The app's `Info.plist` needs `GADApplicationIdentifier` = `$(WL_ADMOB_APP_ID)` and
  `WLAdsDeclared` = `$(WL_ADS_DECLARED)`. The template's post-build phase adds the rest:
  `WLAds`, `WLAdsUnits`, `WLAdsTracking`, Google's `SKAdNetworkItems` and, with tracking on,
  `NSUserTrackingUsageDescription` (from `ads.ios.trackingMessage`).
- The phase edits the built Info.plist, so keep user-script sandboxing off for the app target
  (XcodeGen's default).
- Swift packages link in Release too (SPM can't drop them per configuration), but `AdsService`
  installs nothing and the SDK never starts unless `WL_ADS` is `on`.

**4. In an app's own repo:** ads need no extra Gradle plugin line (unlike Firebase's two), so the
root `build.gradle.kts` stays as it is. Copy the foundation's `build.gradle.kts` as usual.

## Native ads

One AdMob Native advanced ad as an item in a list, drawn by the foundation's fixed template. The app
declares the placement, says where the item goes, and themes it only through its design tokens. Every
house rule below applies to it exactly as to a banner (same `AdsController` state, same `setHidden`,
same ATT wait, `npa=1` with `ads.nonPersonalized`).

**Declare it:**

```kotlin
ads { native("homeList") }                          // Android, in the app definition
```

```swift
ads: [AdPlacement(name: "homeList", underTab: nil, kind: .native)]   // iOS, in AppDefinition
```

A native placement never has a tab (the shell rejects one). Its units are
`ads.android.native.homeList` / `ads.ios.native.homeList`; Debug always uses the test units above.

**Place it.** Android: hoist the slot at screen level, outside the `LazyColumn`, and add the item as a
keyed list item only while it has loaded:

```kotlin
val ad = rememberNativeAd("homeList")
LazyColumn {
    items(rows.take(3), key = { it.id }) { Row(it, Modifier.animateItem()) }
    if (ad.isLoaded) item(key = "ad-homeList") { NativeAdItem(ad, Modifier.animateItem().padding(vertical = AppTheme.spacing.md)) }
    items(rows.drop(3), key = { it.id }) { Row(it, Modifier.animateItem()) }
}
```

iOS:

```swift
@State private var ad = NativeAdSlotModel(placement: "homeList")

List { …; if ad.isLoaded { NativeAdItem(slot: ad) }; … }
    .task { await ad.observe() }
```

Nothing takes space and nothing is composed until an ad has loaded; a failed load is silent (no gap,
no error). With no `NativeAdHost` (no SDK linked, ads off, Release with `ads.enabled=debug`) the slot
never loads.

**When it loads and refreshes** (`NativeAdRotation`, shared, one per placement for the process):

- A *visit* is the time the list is on screen (started) with the rules at `Ready`. Requests happen
  only when a visit begins: never off screen, never in the background, at most one at a time, and at
  least 60 s apart per placement.
- A shown ad is never swapped mid-visit. Coming back within 60 s (detail and back, a tab flip) shows
  the same ad on the first frame with no request: the slot starts from the ad the placement holds
  (`NativeAdRotation.peek()`, which begins, requests and destroys nothing) when the rules are already
  `Ready`, so the row doesn't jump in a frame later and an insert animation doesn't replay. After 60 s the same ad shows and a new one is requested; it
  appears on the next visit.
- An ad is shown at most 1 h after it loaded; an older one is destroyed and a new one requested.
- An ad that loads after the user left is kept for the next visit, not shown in a dead slot.
- **One live slot per placement.** The rotation (and its visit) is shared by every slot of a placement,
  so show a placement on one screen at a time. Two screens alive at once (two tabs kept composed, a
  split view) each need their own placement name (`native("homeList")`, `native("feedList")`);
  otherwise one screen leaving ends the other's visit and a promoted ad can replace the one it draws.
- Ad-free starting, `setHidden`, or consent changing hides the item at once. An ad-free window or a
  consent hide (pending, failed, or no ads allowed) also destroys the placement's held ads, and the one
  still loading when it lands, so an ad is never shown under rules other than those it was requested
  under; the next visit after the rules allow ads again requests a new one. `setHidden` (and the other
  reasons) keep held ads until they expire. A slot applies this when it sees the rules hide: a screen
  that isn't open meanwhile drops its held ads on its next appearance, and they are at most 1 h old.

**The template** (the same on both platforms, sizes in dp/pt; spec in Munch's
`docs/design/` native ad row): a full-width card, fixed height **184** at the default text size (it
grows with the font scale, `NativeAdTemplate.height(fontScale)`, computed once so it never jumps on
screen; iOS caps Dynamic Type at accessibility2), `surface` fill, 1 dp `outlineVariant` border,
`corners.large`. An "Ad" chip ("Annonce" in French, ≥ 16, `onSurface` on `surface`) top-left with the
advertiser beside it; the top-right 24×24 corner is left to the SDK's AdChoices icon. The media
(image or muted video with the SDK's controls) is 120×120 on the left, scaled to fit, never cropped.
Then the headline (2 lines), body (2 lines), the advertiser's icon (40, square) and an outlined CTA
(`outline` border, `primary` text, 1 line). Text is shown as the ad gives it and only truncated with
an ellipsis. Missing optional assets leave no gap; an ad without a headline or media is not shown.
Only the asset views open the ad: no click, swipe or long-press on the card.

**What an app may theme:** only its tokens: `colorScheme` / `style.colors` (`surface`, `onSurface`,
`onSurfaceVariant`, `outline`, `outlineVariant`, `primary`), its type roles and fonts
(`titleSmall`, `bodySmall`, `labelSmall`, `labelLarge` on Android; the brand body font on iOS),
`spacing` and `corners`. There's no app-supplied layout.

**What the app still owns** (AdMob native policy):

- Not the first item in a list, and not directly under a header or total.
- At least 16 dp/pt between the card and any other button (put `spacing.md` above and below it).
- Never styled or counted like the list's content (no content badges, totals or filters).
- Insertion: if the ad loads while its slot is on screen, animate it in (Android `Modifier.animateItem()`,
  iOS the list's row transition) instead of letting rows jump, and not while a finger is down on the
  list; under reduced motion it appears instantly. Off screen it simply appears.
- Store screenshots must not show a native ad, or show it clearly labelled.

**Google's Native Ad Validator** shows over test native ads in Debug on both platforms. It's how QA
checks the template (see the checklist). On Android it can be turned off with the manifest flag
`com.google.android.gms.ads.flag.NATIVE_AD_DEBUGGER_ENABLED=false`; don't, for QA.

## House rules

Enforced in `AdGate`, not by apps. In this order, the first that applies hides the banner:

1. Ads off in this build.
2. The placement isn't declared.
3. The app hid the placement (`setHidden`, see "App hooks").
4. No ad request before consent is `Resolved(canRequestAds = true)`. A consent that is pending,
   failed or declined for ads keeps banners hidden.
5. No ads on launch 1 (the launch counter lives in `ads-launches.json`).
6. No ads during an active ad-free window.
7. Banners and native ads only: there's no interstitial or rewarded API.

The Google SDK starts only when the first banner or native ad is allowed to load, after all of this, never at
startup. Nothing else is placed for you: the shell refuses a placement under the Settings tab and
under the sign-in gate (`No ads under settings: settings and sign-in screens stay ad-free`), and a
tab holds one banner. Shared features never depend on ads.

The banner is labelled "Ad" ("Annonce" in French), takes no height while it is hidden, loading or
failed, and never shows an error. A failed refresh keeps the ad already on screen.

## Ad-free (rewards, purchases)

`AdFree` is in `:core:ads:shared`, so an app's shared code can grant it on either platform:

```kotlin
val adFree: AdFree = getKoin().get()                 // or inject it
adFree.extend(1.hours)                               // adds to the later of now and the current end
adFree.grantUntil(Clock.System.now() + 7.days)       // never shortens an active window
adFree.state                                         // StateFlow<AdFreeState>: Inactive | Active(until)
```

From Swift: `SharedGraph.shared.adFree()` (nil in an app without ads). The window is stored in
`ads-free.json`, ends by the clock without a restart, and survives relaunches. Banners hide as soon as
it starts and come back when it ends. Nothing in the foundation sells ad-free: the app decides what
grants it.

## App hooks

Two calls for an app's shared code (Koin: `get<AdsController>()`, `get<AdFree>()`; Swift:
`SharedGraph.shared.adsController()`, `SharedGraph.shared.adFree()`, both nil without ads):

```kotlin
ads.setHidden("home", true)    // hide the placement, e.g. while its screen is empty
ads.setHidden("home", false)   // back to the house rules
adFree.clear()                 // end the ad-free window now (refunds, a debug tool)
```

```swift
SharedGraph.shared.adsController()?.setHidden(placement: "home", hidden: true)
try await SharedGraph.shared.adFree()?.clear()
```

- `setHidden` only hides: consent, first launch, ad-free time and ads-off still decide when a banner
  may show. A hidden placement never loads an ad or asks for tracking. It's idempotent, a no-op for an
  undeclared placement or with ads off, and lasts for the process (not stored), so set it from live
  state.
- `clear()` is the only call that shortens a window. Banners come back as when a window runs out.

## iOS tracking (ATT)

Off by default. With `ads.ios.requestTracking=true` (and the message), the prompt is shown the first
time a banner would load: after consent is resolved, never at startup and never on launch 1. Banners
wait for the answer. Unless the app sets `ads.nonPersonalized=true` (below), the foundation doesn't
force non-personalized requests; whatever the user and UMP decide applies. Without the key, the app never asks and doesn't need
`NSUserTrackingUsageDescription`.

## Non-personalized ads

`ads.nonPersonalized=true` in `app.properties` makes every banner and native request carry AdMob's `npa=1`
extra, whatever consent and ATT allow:

| | How the request carries it |
|---|---|
| Android | `AdRequest.Builder().addNetworkExtrasBundle(AdMobAdapter::class.java, bundle)` with `npa` = `"1"` |
| iOS | `Request.register(Extras)` with `additionalParameters = ["npa": "1"]` (`GADExtras` in Objective-C) |

- Default `false`: the request is the plain `AdRequest.Builder().build()` / `Request()` it was
  before, so an app that doesn't set the key sends the same requests as before.
- The setting is published only for a build type where ads are on (Android's
  `foundation_ads_non_personalized`, iOS's `WL_ADS_NON_PERSONALIZED`, which the `WhitelabelAdsApp`
  template's phase copies to `WLAdsNonPersonalized` in the built Info.plist). The app's own
  Info.plist needs nothing new.
- What it does: Google doesn't use the request to personalize the ad. What it doesn't do: consent is
  still gathered (UMP's storage and measurement choices still apply), and Google still uses the
  request for frequency capping, fraud prevention and measurement. Sensitive ad categories and the
  maximum content rating are AdMob console settings, not code.
- Analytics user properties never carry health data ([firebase.md](firebase.md)), with or without
  this switch.

## Privacy choices

The existing `PrivacyChoicesRow()` ([firebase.md](firebase.md)) serves ads too: it reopens UMP's
privacy options form while UMP requires one. There is no separate ads row. An app with ads but no
Firebase gets consent from the same shared module, and the row appears there too.

## Debug switches

Same as Firebase's ([firebase.md](firebase.md)): Debug only, markers under `files/debug/` on
Android and launch arguments on iOS. Consent switches apply to the fake consent port only, which a
Debug build uses when Firebase runs fake (no config file or no app ID).

| What | Android marker | iOS argument |
|---|---|---|
| Consent fails, so ads stay hidden | `fake-consent-failed` | `-fakeConsent failed` |
| Consent obtained (default) | `fake-consent-obtained` | `-fakeConsent obtained` |

The ad-free window and launch counter are plain JSON in the app's store. To start an ad-free window
in a Debug build (Android; the iOS file is `datastore/ads-free.json` under the app's Application
Support folder):

```sh
adb shell "run-as com.whitelabel.showcase.debug sh -c 'echo {\\\"untilEpochMillis\\\":4102444800000} > files/datastore/ads-free.json'"
adb shell run-as com.whitelabel.showcase.debug rm files/datastore/ads-free.json     # back to normal
```

Restart the app after either change. Delete `ads-launches.json` (or clear the app's data) to be on
launch 1 again.

## Release check

`scripts/verify-apps.sh release` fails when a release dex contains the AdMob SDK
(`com/google/android/gms/ads`, `MobileAdsInitProvider`) while `ads.enabled` is not `true`, and it
checks that `foundation_ads` and `foundation_ads_declared` match `app.properties`. With
`ads.enabled=debug`, Release has none of it. iOS Release may link the package but starts nothing.

## Checking it

Unit tests need no AdMob account. The checks that need a real device, a network and (for ATT) a
simulator or phone are in [the checklist](superpowers/checklists/2026-11-12-ads-manual.md).
