# Firebase and Ads on KMP (Android + iOS) — Design

Status: draft for the owner's review (2026-11-12)
Amends sections 5 (Firebase) and 6 (Ads) and roadmap rows 4–5 of
[`2026-11-10-standalone-app-support-design.md`](2026-11-10-standalone-app-support-design.md).
Everything there still holds unless this document says otherwise. The KMP rules come from
[`2026-11-11-ios-kmp-stack-design.md`](2026-11-11-ios-kmp-stack-design.md) and the iOS starter from
[`2026-11-11-ios-starter-design.md`](2026-11-11-ios-starter-design.md).

## 0. Intent and the why

The owner tests many small apps quickly, earns from ads that don't put users off, and doubles down
on the apps that take. The first is **Munch**. For that, every app needs:

- **Remote tuning, usage analytics and crash reports without running a backend.** This means
  Firebase Remote Config, Analytics and Crashlytics.
- **Clean ads.** A banner, with consent, house rules built in, and an ad-free window the app can
  grant.

These were roadmap rows 4 and 5 and were never built. Meanwhile the foundation moved to KMP: shared
Kotlin, with Compose screens on Android and SwiftUI screens on iOS. So both pieces now have to work
on **both platforms**. Munch launches on Play first and on the App Store after, both on this
foundation.

### Decisions the owner made (2026-11-12)

1. **Both platforms in this round.** The shared core plus real Android and iOS implementations. iOS
   is not stubbed.
2. **The iOS tracking prompt (App Tracking Transparency, ATT) is a per-app switch**,
   `ads.ios.requestTracking`, off by default. When it's on, the prompt comes with the first ad, after
   consent is resolved, and never at startup. Apps can compare revenue with and without it.
3. **Showcase proves it, in Debug builds only.** Showcase turns Firebase and ads on for Debug, and
   its E2E suites on both platforms cover the house rules. Release showcase behaves exactly as
   today. This replaces the earlier non-goal "showcase doesn't adopt Firebase or ads" for Debug
   only.
4. **Approach A** (section 2): shared Kotlin owns the rules; thin Swift classes own the Apple-side
   SDK calls and are handed to Koin at startup.
5. **Consent is asked at first launch, only where the law requires it** (EEA/UK, as Google's UMP
   decides). Analytics and crash reports wait for it, and ads reuse the same answer. This replaces
   the parent spec's "consent form the first time an ad would load, never at startup". Users
   elsewhere see nothing new. Reason: day-1/day-7 retention is the point of the market test, so
   launch 1 must be measured, and measured lawfully.
6. **A user who declines still gets ads**: Google's limited, non-personalized ads, Google's standard
   behavior. Only a consent error or a dismissed form means no ads (retried next launch).
7. **Showcase's Firebase config files stay out of git** (the repo is public). Showcase Debug falls
   back to a fake Firebase when they're missing, so CI stays green. The owner drops the real files
   in locally for manual checks.

Unchanged from the parent spec, and binding:
- **Ads:** banner only; no ads on first launch, on forms, settings or in notifications; consent
  (Google UMP) resolved before the first ad request; an ad-free window apps can grant; Debug builds
  always use Google's test ad IDs.
- **Firebase:** remote values with defaults from the app's code; real `Analytics`/`CrashReporter`
  behind the existing interfaces; no Analytics or Crashlytics collection in Debug by default.
- **Feature flags:** `RemoteValues` is not merged with the backend's `app/config` flags.

### Goals

- Any app turns on Firebase, ads, or both with config keys and one DSL line per platform. It writes
  no SDK code.
- The same rules hold on both platforms because they are the same Kotlin: ad gating, the ad-free
  window, the first-launch count, typed remote values and their fallbacks.
- An app can grant ad-free time and read remote values from shared code (for Munch: its ViewModels).
- Showcase Debug shows all of it working on Android and iOS, with automated checks for the visible
  house rules.

### Non-goals

- Interstitial, rewarded, app-open or native ad formats. The API leaves room for native ads.
- Merging Remote Config with the backend's feature flags.
- Mediation, A/B testing, Firebase Messaging, Performance or Auth.
- Hosted iOS CI. iOS checks stay local (`scripts/check-ios.sh`).
- Munch adopting the modules. That is Munch's own work once these land.

### Anti-goals (a failure even if it works)

- Release showcase changing on either platform: no banner, no consent form, no ATT prompt, no
  network calls to Firebase or Google ads. On Android its release APK carries no Firebase, ads or
  UMP code. On iOS, Release may link the packages (SPM can't drop them per configuration) and may
  carry the test `GADApplicationIdentifier` if the linked SDK requires it, but never starts them.
- Analytics or crash data leaving the device before consent is resolved where consent is required.
- An app without Firebase or ads paying for them: no SDKs linked into Android builds, no Swift
  packages in its Xcode project, no Info.plist keys.
- An ads API that makes it easy to break a house rule on either platform.
- App identity in shared modules: Firebase files, ad unit IDs, the ATT message.
- The platforms disagreeing on a rule because each one reimplemented it.

## 1. Constraints

- The foundation repo is **public**. Anything committed is world-readable, which is why showcase's
  Firebase config files stay out of git (decision 7).
- No app identity outside `apps/<name>/` (Android) and `ios/<App>/` (iOS). `scripts/verify-apps.sh`
  checks every built Android app.
- DI is Koin. iOS starts the graph with `SharedGraph.start(platform:definition:appModules:useMockApi:)`.
  Swift implementations reach Kotlin as objects passed to that call, as the reminders' platform
  pieces already do.
- The iOS app links one static framework named `Shared` per app. Kotlin must not link Apple-only
  SDK frameworks unless the app turned the feature on.
- GitHub CI is Android-only (Linux). The Gradle build must work without Xcode or the Swift SDKs.
- Git and PR rules as in `CLAUDE.md`. The owner merges.

## 2. Approaches considered

| | Approach | Trade-off | Verdict |
|---|---|---|---|
| **A** | **Kotlin owns rules and interfaces; Swift owns the iOS SDK calls** (Firebase and Google Mobile Ads via Swift Package Manager), handed to Koin at startup | Official SDKs used as documented; Kotlin never links Apple frameworks; a thin Swift layer per service | **Chosen by the owner** |
| B | GitLive's Firebase Kotlin SDK for Firebase; Swift for ads (no KMP AdMob/UMP library exists) | Firebase written once in Kotlin, but two patterns, and Firebase's iOS frameworks must link into the static `Shared` framework, which tends to break on SDK updates | Rejected |
| C | Kotlin cinterop straight to the iOS SDKs | No Swift, but hand-kept bindings to Objective-C headers that change every release | Rejected |

## 3. Module layout

It follows the reminders split: a KMP `:shared` module with the rules, and Android modules that hold
the SDK, plus Swift in its own Xcode target. **Android splits each service into an API module and an
SDK module (Claude's call).** The API module has no Google SDK and is linked into every build of an
app that uses the service: the DSL, the `AdBanner` composable, `AdFree`, `RemoteValues`. The SDK
module is linked only into the build types where the service is enabled (`debugImplementation` for
`debug`), and contributes its Koin bindings through the `ModuleContributor` mechanism the mock
backend already uses. Without its SDK module, a service runs in "off" mode: the banner collapses,
remote values return defaults, and analytics logs. That lets showcase's `src/main` call `ads { }`
and `AdBanner` while its release dex holds no Google classes.

```
core/consent/shared    KMP   ConsentState, the "resolved" rule, ConsentPort, startup gathering
core/consent/google    Android  UMP SDK implementation of ConsentPort
core/firebase/shared   KMP   RemoteValues engine (typed reads + fallbacks), ports, Koin module
core/firebase/google   Android  Firebase SDK: RemoteConfig source, Analytics, CrashReporter
core/ads/shared        KMP   AdGate, AdFree, launch counter, tracking sequencing, ports
core/ads               Android  API: AdBanner composable (collapses without the SDK), AdPrivacyRow, DSL
core/ads/google        Android  Google Mobile Ads SDK: banner host, initialization
core/domain            KMP   + RemoteValues interface, DefaultsOnlyRemoteValues
ios/WhitelabelConsent        Swift target (UMP package)
ios/WhitelabelFirebase       Swift target (FirebaseRemoteConfig/Analytics/Crashlytics packages)
ios/WhitelabelAds            Swift target (GoogleMobileAds package), AdBanner view, AdPrivacyRow
```

- **`RemoteValues` lives in `:core:domain`**, together with a defaults-only implementation, so
  features and app code can depend on it whether or not the app turned Firebase on. The shell binds
  the defaults-only implementation unless Firebase is on.
- **Consent is its own small piece** because both Firebase and ads depend on it, and neither may
  depend on the other. An app that turns on Firebase or ads gets it automatically.
- **Separate Swift targets**, `WhitelabelConsent.yml`, `WhitelabelFirebase.yml` and
  `WhitelabelAds.yml`, sit beside `WhitelabelKit.yml`. Only an app that turns a service on includes
  its targets, and with them the Swift packages. WhitelabelKit itself never imports Firebase, UMP or
  Google ads.
- **Builder's call:** whether the Swift targets are separate frameworks or extra source folders the
  app target includes, as long as an app without the service links none of the SDKs.

## 3a. Consent (shared by Firebase and ads)

- **When:** on every launch of an app with Firebase or ads, after the first screen appears (never
  blocking the splash), call UMP's consent-info update and, if UMP says a form is required, show it.
  UMP only requires it where the law does (EEA/UK), so most users never see anything. A user who
  already answered isn't asked again until UMP says so (e.g. the consent string expired).
- **`ConsentState`** (shared): `Unknown` (not yet asked this launch) / `Resolved(canRequestAds,
  analyticsAllowed)` / `Failed` (network error or form dismissed: retried next launch).
  - "Resolved" means UMP reports `OBTAINED` or `NOT_REQUIRED`.
  - `analyticsAllowed` follows the user's choice (the TCF storage purpose, or `NOT_REQUIRED`).
    Builder's call: how it's read from UMP on each platform.
  - A user who declines is still `Resolved` with `canRequestAds = true` (decision 6), and gets
    Google's limited ads.
- **Firebase follows it:**
  - Analytics and Crashlytics collection start disabled in every build. They are enabled once
    consent is `Resolved`. Analytics' storage consent (Firebase Consent Mode) is set from
    `analyticsAllowed`, so a user who declined is measured without identifiers.
  - Crash reports are enabled on any `Resolved` state. Claude's call: crash reports are needed for
    the app to work and hold no personal data; revisit if legal advice differs.
  - Before resolution, events are dropped, not queued.
- **Ads follow it:** no ad request until `Resolved` with `canRequestAds`. On `Failed`, no ads this
  launch.
- **Privacy options:** `privacyOptionsRequired` and `showPrivacyOptions()` live here. `AdPrivacyRow`
  (5.3) uses them, and it's named for ads but also covers analytics. Builder's call: a neutral name
  such as `PrivacyChoicesRow`.
- **Debug:** a Debug-only switch forces the EEA geography (UMP's debug settings) and resets consent,
  for manual checks. Fake mode (section 7) resolves instantly unless a test says otherwise.

## 4. Firebase

### 4.1 Config and opt-in

`app.properties`:

| Key | Values | Effect |
|---|---|---|
| `firebase.enabled` | `false` (default), `true`, `debug` | `debug`: Debug builds only. Exists for showcase; any app may use it while testing |

**Missing config files (decision 7).**
- With `true`, a missing config file **fails the build** on that platform.
- With `debug`, a missing file is allowed. That build uses the fake Firebase (fake
  `RemoteConfigSource`, logging analytics) and says so once in the log.
- Showcase's `google-services.json` and `GoogleService-Info.plist` are gitignored.
  `docs/` explains where to drop them for manual checks.

**Android**
- When enabled, the application plugin applies the `google-services` and Crashlytics Gradle plugins
  and adds `:core:firebase:google`. With `debug`, the dependency is `debugImplementation`, and
  release variants neither need nor process the config file.
- With `true`, the build **fails** if `apps/<name>/google-services.json` is missing, or doesn't list
  the enabled variants' application IDs. Debug IDs end in `.debug`.

**iOS**
- `generateIosConfig` writes `WL_FIREBASE_ENABLED` per configuration into the xcconfigs.
- With `true`, it **fails** if `ios/<App>/<App>/GoogleService-Info.plist` is missing.
- The app's `project.yml` includes `WhitelabelFirebase.yml`.
- Swift starts Firebase (`FirebaseApp.configure()`) only in a configuration where it is enabled.
- With `debug`, Release builds may still *link* the Firebase packages, because SPM can't drop a
  package per configuration. They never configure or start Firebase, so release behavior and network
  traffic match today's.

### 4.2 `RemoteValues` (shared)

```kotlin
interface RemoteValues {               // :core:domain, commonMain
    fun long(key: String, default: Long): Long
    fun double(key: String, default: Double): Double
    fun string(key: String, default: String): String
    fun boolean(key: String, default: Boolean): Boolean
    fun <T> json(key: String, default: T, serializer: KSerializer<T>): T
    val changes: Flow<Unit>            // emits after each activation that changed values
}
```

- **Defaults always come from the caller's code.** A missing key, an empty value, a value of the
  wrong type or garbled JSON returns the default and never throws.
- The parsing and fallback logic is written once, in `:core:firebase:shared`, over a raw port:

  ```kotlin
  interface RemoteConfigSource {
      fun raw(key: String): String?                 // the activated value, or null
      suspend fun fetchAndActivate(): Boolean       // true when values changed
  }
  ```

  Android implements it with the Firebase Android SDK, and iOS with a Swift class over
  `RemoteConfig.remoteConfig()`.
- **Fetching:**
  - fetch-and-activate once per launch, in the background, off the startup path;
  - later launches start from the cached activated values;
  - Debug uses a short minimum fetch interval, the parent spec's rule. Builder's call: 0 s in Debug,
    12 h in Release (Firebase's default).

### 4.3 Analytics and crash reports

- Android: `FirebaseAnalytics` and `FirebaseCrashReporter` implement the existing `:core:analytics`
  interfaces.
- iOS: Swift classes implement the same Kotlin interfaces (`Analytics`, `CrashReporter`) over the
  Firebase iOS SDK.
- The existing `AnalyticsEvent` rules still apply: snake-case names of at most 40 characters, and no
  personal data in params.
- **Kotlin crashes on iOS** must reach Crashlytics with Kotlin stack frames, both uncaught Kotlin
  exceptions and `CrashReporter.record(throwable)`. The likely tool is Touchlab's **CrashKiOS**.
  It is added to the app's `Shared` framework **only when Firebase is enabled**, because it needs
  the Crashlytics symbols at link time. dSYM upload is wired into the app's Xcode build when
  Firebase is enabled.
- **Collection follows consent** (section 3a): off until consent is resolved, in every build.
- **Collection in Debug:**
  - Analytics and Crashlytics collection is **off** in Debug, so test sessions don't pollute the
    numbers.
  - A Debug-only switch turns collection on and routes events to Firebase's live **DebugView**: a
    launch argument on iOS (`-analyticsDebug`, which also sets Firebase's `-FIRDebugEnabled`), and on
    Android the equivalent debug option plus `adb shell setprop debug.firebase.analytics.app <id>`,
    documented.
  - Release collects normally.

### 4.4 Wiring

**Android DSL:** `appDefinition { firebase() }` binds `RemoteValues`, `Analytics` and
`CrashReporter` to the Firebase ones. Calling `firebase()` together with `analytics(...)` or
`crashReporter(...)` fails at startup with a clear message, keeping the "no duplicate bindings"
rule. `firebase()` without `firebase.enabled` fails with the key to add, and so does the reverse.

**iOS:** the Swift `AppDefinition` gets the equivalent one-liner. Builder's call on the name, e.g.
`AppDefinition(style:services: [.firebase]) { … }` or a `.firebase()` modifier. Starting the graph
then:
1. configures Firebase if this configuration has it enabled;
2. passes the Swift `RemoteConfigSource`, `Analytics` and `CrashReporter` into `IosPlatform` (new
   optional members) or as an app Koin module;
3. starts the fetch.

An app on iOS never touches Koin or the SDK.

## 5. Ads

### 5.1 Config and opt-in

`app.properties`:

| Key | Values / format | Notes |
|---|---|---|
| `ads.enabled` | `false` (default), `true`, `debug` | As `firebase.enabled` |
| `ads.android.appId` | `ca-app-pub-…~…` | Manifest meta-data via placeholder |
| `ads.ios.appId` | `ca-app-pub-…~…` | Info.plist `GADApplicationIdentifier`, via xcconfig |
| `ads.android.banner.<placement>` | `ca-app-pub-…/…` | One per placement |
| `ads.ios.banner.<placement>` | `ca-app-pub-…/…` | One per placement |
| `ads.ios.requestTracking` | `false` (default), `true` | Owner's decision 2 |
| `ads.ios.trackingMessage` | text | Info.plist `NSUserTrackingUsageDescription`; required when `requestTracking=true` |

- **Validation** at build time: Gradle for Android, `generateIosConfig` for iOS. When ads are
  enabled, each platform's app ID and each declared placement's unit ID must be present and well
  formed for that platform.
- **Debug always uses Google's public test IDs** (app and banner) on both platforms, whatever the
  properties say. With `ads.enabled=debug`, which is what showcase uses, the properties may omit
  real IDs entirely.
- **iOS Info.plist when ads are enabled:** `GADApplicationIdentifier`, Google's `SKAdNetworkItems`
  list, and, only with tracking on, `NSUserTrackingUsageDescription`. Builder's call: whether
  `generateIosConfig` writes them or the app's plist pulls them from the xcconfig plus a
  kit-provided fragment. An app without ads gets none of these keys.
- **Placements** are declared in the DSL on each platform: `ads { placement("home") }` on Android,
  and the Swift equivalent. A placement declared in the DSL but missing in properties, or the
  reverse, fails the build or startup with a clear message. In Debug, a declared placement just uses
  the test unit.
- **Where a banner goes (Claude's call).** There are two ways, both on both platforms:
  - **The app's own screen:** the app calls `AdBanner("home")` wherever it wants it. This is how
    Munch places its Home banner.
  - **Under a tab, from the shell:** `placement("home", underTab = HomeRoute)`. The shell draws the
    banner below that tab's root screen. This is how a banner goes under a *shared* feature's screen
    without the feature knowing about ads, and how showcase places its banner.
  - The shell refuses a placement under the Settings tab, and the auth gate never shows banners
    (house rule: no ads on forms or settings). Shared features never depend on ads.

### 5.2 Shared rules (`:core:ads:shared`)

- **`AdGate`**, a pure function. Its inputs are `consent` (the `ConsentState` from section 3a),
  `launchNumber`, `adFreeUntil` vs `now`, whether the placement is
  declared, and `adsEnabled`. Its output is `AdDecision.Show` or `AdDecision.Hide(reason)`.
  - **No ad request before consent is resolved** (`Resolved` with `canRequestAds`, section 3a).
    `Unknown` and `Failed` hide the banner.
  - No ads on the **first launch**. The launch counter is persisted and counts app process starts.
  - No ads while an **ad-free window** is active.
  - An undeclared placement never shows. In Debug it fails loudly instead.
- **`AdFree`**, shared and persisted with `JsonStore`, using `kotlin.time.Clock` (injectable):
  - `grantUntil(instant)`, `extend(by: Duration)`, and `state: StateFlow<AdFreeState>` (active until
    / inactive);
  - `extend` stacks onto an active window, or starts from now;
  - wall clock: a user who moves the clock forward only shortens their own window, as in the parent
    spec. How time is earned is the app's business.
- **Tracking sequencing**, shared, over a port. Consent itself comes from section 3a and was
  already gathered at launch:

  ```kotlin
  interface TrackingPort {          // iOS ATT; Android binds a no-op "not applicable"
      suspend fun requestIfNeeded()  // shows the system prompt only when status is notDetermined
  }
  ```

  The first time a banner would load, and only if every other rule already says `Show` (which
  includes consent `Resolved` with `canRequestAds`):
  1. on iOS with `ads.ios.requestTracking=true`, request tracking;
  2. then load.

  - The tracking prompt never appears before consent is resolved, at startup, without ads enabled,
    or with the switch off. Because of the first-launch rule it can't appear before launch 2.
  - With the switch off, the app simply never asks. No IDFA is available, and AdMob decides
    personalization from consent and its other signals. **The foundation does not force
    non-personalized requests (Claude's call).** The switch's comparison is then exactly "ask vs
    don't ask", which is what the owner wants to measure.
  - Builder's call: whether the ATT step uses UMP's own IDFA explainer message. Owner-facing
    behavior must match the sequence above either way.
- **`AdsController`**, shared, exposes `bannerState(placement): StateFlow<BannerState>`
  (`Hidden(reason)` / `Ready`). Both platforms' banners observe it, so the same flow drives Compose
  and SwiftUI.

### 5.3 Platform pieces

| | Android (`:core:ads` + `:core:ads:google`) | iOS (`WhitelabelAds`) |
|---|---|---|
| SDK | Google Mobile Ads (Gradle), in `:core:ads:google` only | GoogleMobileAds (SPM) |
| Banner | `AdBanner(placement, modifier)` composable in `:core:ads`; draws through a banner host the SDK module provides, collapses without it | `AdBanner(placement:)` SwiftUI view wrapping `BannerView` |
| Size | adaptive anchored banner for the available width | same |
| Ports | `TrackingPort` no-op | `TrackingPort` (ATT) |
| Privacy row | `AdPrivacyRow()` for an app's `SettingsSection` | `AdPrivacyRow()` for an app's `SettingsSection` |

- **The banner** is labelled "Ad" (localized in the kit's strings) and **collapses to zero height**
  when it is hidden, while it loads, and when it fails. It never shows an error.
- **`AdPrivacyRow`** is visible only when `privacyOptionsRequired` is true, and opens UMP's privacy
  options. The shared Settings feature does not depend on ads. The app places the row in its own
  section.
- **DSL:** Android `appDefinition { ads { placement("home") } }`; iOS, the Swift equivalent next to
  the Firebase one. The app's code then only places `AdBanner("home")` where it wants the banner and
  calls `AdFree` from its own shared code or ViewModels.

## 6. Showcase (Debug only)

- `apps/showcase/app.properties`: `firebase.enabled=debug`, `ads.enabled=debug`.
- Android: `firebase()` and `ads { placement("home", underTab = HomeRoute) }` in
  `showcaseDefinition()`, called unconditionally. In Release, the services run in "off" mode (no
  SDK module linked), so the calls do nothing. Swift does the same. The banner sits under the Home
  tab through the shell, and the privacy row in a showcase Settings section.
- Showcase's Firebase config files are gitignored (decision 7). Without them, showcase Debug runs
  fake Firebase.
- A **Debug-only "Grant 1 minute ad-free"** action, in showcase's own Settings section, exercises
  `AdFree` from the UI.
- Release showcase gets none of this: the anti-goal above. On Android, `verify-apps.sh release`
  fails if the release dex contains `com/google/firebase`, `com/google/android/gms/ads` or
  `com/google/android/ump`, the same mechanism as the mock-backend check.
- Remote values in showcase: one harmless Debug-visible value shows that a fetch arrived, e.g. a
  Home greeting suffix or an About line read from `RemoteValues` with a default. Builder's call.

## 7. Testing

**Unit (commonTest, both platforms through `check.sh` and the iOS test task):**
- Remote values: typed reads, missing/empty/wrong-type/garbled values return the default, JSON with
  a serializer, `changes` emits only when activation changed values.
- `AdGate`: the full truth table (consent × launch number × ad-free × placement × enabled).
- `AdFree`: grant, extend stacking, expiry, and survival across a store reopen, with a fake clock.
- Launch counter: persisted, first launch is 1.
- Consent: the state mapping from UMP statuses; declined is still `Resolved` with ads allowed and
  analytics storage denied; `Failed` means no ads and no collection this launch.
- Firebase collection: nothing is sent before `Resolved`; storage consent follows
  `analyticsAllowed`.
- Sequencing with fake ports:
  - tracking only after consent is resolved, only with the switch on, never at startup or on launch
    1;
  - a consent failure means no load;
  - the tracking port is never called on Android.

**Build logic and config:**
- Property validation: enabled values, paired platform IDs, the tracking message required with
  tracking, placements matching.
- A missing `google-services.json` fails the build; `debug` mode leaves release untouched.
- `generateIosConfig`: the same rules for iOS, plus a missing `GoogleService-Info.plist` fails.

**`verify-apps.sh`:** release APKs carry no Firebase, ads or UMP classes unless the app enables them
for release.

**E2E, showcase Debug, Android and iOS:**
- **Ads:** both platforms' suites gain a **fake ads SDK**, enabled like the mock backend
  (`-mockApi`-style flag / Debug launch argument `-fakeAds`):
  - the fake consent is resolved, the fake banner loads instantly, and the fake banner has a test
    tag.
  - Tests:
    - **first launch shows no banner**;
    - the **second launch shows it**;
    - after "Grant 1 minute ad-free" **it hides**;
    - with fake consent "required, then declined", it **still shows** on the second launch (limited
      ads);
    - with fake consent "failed", **it doesn't show** that launch.
  - Real network ad loads are not in automated tests, because they're flaky.
- **Firebase:** remote values come through a fake `RemoteConfigSource` in the same fake mode, and the
  showcase line shows the faked value.

**Manual checklist** (in the PRs, on a device or simulator per platform, using showcase Debug and
the owner's showcase Firebase project):
1. A Remote Config value set in the console arrives after relaunch.
2. Events appear in DebugView with the debug switch on, and not without it.
3. A forced test crash appears in Crashlytics. On iOS, a Kotlin exception shows Kotlin frames.
4. With the debug geography set to EEA, the UMP form appears on **first launch**. No analytics event
   reaches DebugView before it's answered. The privacy row then shows. Outside the EEA, nothing
   appears.
5. iOS with `requestTracking=true`: the ATT prompt follows the consent form, at the first banner and
   never at startup.
6. A Google test banner loads on the second launch, on both platforms.

## 8. Roadmap (branches in this repo)

| # | Branch | Deliverable |
|---|---|---|
| 1 | `docs/firebase-ads-design` | This document |
| 2 | `feat/firebase` | Consent (`:core:consent:*`, `WhitelabelConsent`), `RemoteValues` + defaults-only binding, `:core:firebase:*`, `WhitelabelFirebase`, CrashKiOS wiring, config keys on both platforms, showcase Debug, tests, docs |
| 3 | `feat/ads` | `:core:ads:*`, `WhitelabelAds`, shell tab placement, config keys and Info.plist keys, `verify-apps.sh` checks, showcase Debug + E2E (fake ads), tests, docs |

- Builder's call: split a row into Android and iOS PRs if one gets too big for one review. Each PR
  still keeps both platforms building.
- After `feat/firebase` lands, write the `ios-platform-services` skill (planned in the KMP design)
  from the real code, and extend it after `feat/ads`.

**Owner prerequisites:**
- A Firebase project for showcase, with two apps: Android `com.whitelabel.showcase.debug` and iOS
  `com.whitelabel.showcase.debug`. It is needed before `feat/firebase`'s manual checks. Unit tests,
  builds and E2E tests don't need it.
- No AdMob account is needed for showcase: test IDs only. Munch needs its own later.

## 9. Claude's calls (for the owner to check)

1. **Each Android service splits into an API module and an SDK module** (section 3), so showcase's
   shared code compiles in Release while its release APK carries no Google SDK.
2. **Banners under shared screens go through a shell tab slot** (5.1), so shared features never
   depend on ads. The Settings tab and the auth gate refuse banners.
3. **iOS Release showcase may link the SDK packages, and may carry the test ad app ID** if the linked
   SDK requires it. It never starts them (anti-goals).
4. **No forced non-personalized ads when the ATT switch is off** (5.2).
5. **Crash reports are enabled on any resolved consent, including declined** (3a).
6. **Consent is its own module set**, shared by Firebase and ads (3, 3a).

## 10. Left to the builder

- Exact Kotlin/Swift names beyond the public API above, the internal shape of the ports, and the DSL
  spelling on iOS.
- Separate Swift frameworks vs source folders for `WhitelabelFirebase`/`WhitelabelAds`, as long as
  an app without the feature links none of the SDKs.
- How Info.plist ad keys and the SKAdNetwork list are produced.
- Fetch intervals (suggested: 0 s Debug, 12 h Release).
- The showcase remote-value demo and where the Debug ad-free action lives.
- SDK versions, pinned in the version catalog (Android) and `project.yml` (iOS).
