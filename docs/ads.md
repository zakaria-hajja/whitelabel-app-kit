# Ads

An app shows AdMob banners without writing SDK code: one config key, a placement per banner and one
line per platform. Banners only. The foundation owns the rules (consent, first launch, ad-free
windows), so an app can't get them wrong. Design:
[2026-11-12-firebase-and-ads-kmp-design.md](superpowers/specs/2026-11-12-firebase-and-ads-kmp-design.md).
Consent (Google's UMP) is shared with Firebase: see [firebase.md](firebase.md).

An app without ads gets nothing: no SDK in the Android build, no Gradle change, no Swift package on
the app target, no Info.plist keys beyond the empty placeholders.

## Modules

| Module | Platform | What |
|---|---|---|
| `:core:ads:shared` | KMP | `AdGate` (the rules), `AdsController` (one banner state per placement), `LaunchCounter`, `AdFree`, `TrackingPort`, and the wiring and placement checks both platforms share |
| `:core:ads` | Android | `AdBanner(placement)`, the `ads { placement(name, underTab) }` DSL, the `BannerHost` interface. No Google SDK |
| `:core:ads:google` | Android | Google Mobile Ads: the `AdView` behind `BannerHost`, started on the first banner |
| `ios/WhitelabelAds` | iOS | Swift target `WhitelabelAds`: `AdsService`, Google's banner view, the ATT prompt. The XcodeGen template `WhitelabelAdsApp` links it and writes the ads keys into the built Info.plist |

The kit's `AdBanner(placement:)` (WhitelabelKit) and the shell's tab slot draw the banner; neither
imports the ads SDK.

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
| `ads.ios.requestTracking` | `false` (default) or `true` | Ask for App Tracking Transparency (see below) |
| `ads.ios.trackingMessage` | text | The ATT prompt's explanation. Required with `requestTracking=true`; must not contain `$(` or `${` |

`<placement>` is a name (`^[A-Za-z][A-Za-z0-9_-]*$`) that the app also declares in code. A unit with
no matching placement, or a placement with no unit (in `true` mode or any Release build), fails the
build or the launch with a message naming the key. Showcase uses `ads.enabled=debug`, so its
properties hold no unit IDs.

**Where the real units go:** AdMob console > Apps > the app > Ad units > Add ad unit > Banner. Copy
its ID (`ca-app-pub-...~` is the app, `.../` with a slash is the unit) into
`ads.android.banner.home` / `ads.ios.banner.home`. Units are public identifiers, like the app IDs, so
they live in `app.properties`.

**Test units.** Debug always uses Google's public test units, whatever the properties say, so a
developer build never serves or counts a real ad:

| | App ID | Adaptive banner |
|---|---|---|
| Android | `ca-app-pub-3940256099942544~3347511713` | `ca-app-pub-3940256099942544/9214589741` |
| iOS | `ca-app-pub-3940256099942544~1458002511` | `ca-app-pub-3940256099942544/2435281174` |

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

## House rules

Enforced in `AdGate`, not by apps. In this order, the first that applies hides the banner:

1. Ads off in this build.
2. The placement isn't declared.
3. No ad request before consent is `Resolved(canRequestAds = true)`. A consent that is pending,
   failed or declined for ads keeps banners hidden.
4. No ads on launch 1 (the launch counter lives in `ads-launches.json`).
5. No ads during an active ad-free window.
6. Banners only: there's no interstitial or rewarded API.

The Google SDK starts only when the first banner is allowed to load, after all of this, never at
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

## iOS tracking (ATT)

Off by default. With `ads.ios.requestTracking=true` (and the message), the prompt is shown the first
time a banner would load: after consent is resolved, never at startup and never on launch 1. Banners
wait for the answer. The foundation never forces non-personalized requests; whatever the user and
UMP decide applies. Without the key, the app never asks and doesn't need
`NSUserTrackingUsageDescription`.

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
