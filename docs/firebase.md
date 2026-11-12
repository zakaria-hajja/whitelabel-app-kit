# Firebase and consent

An app gets remote values, usage analytics and crash reports from Firebase without running a
backend, on Android and iOS. It writes no SDK code: one config key and one line per platform.
Consent (Google's UMP) comes with it, because nothing may be collected before the user has been
asked where the law requires it. Design:
[2026-11-12-firebase-and-ads-kmp-design.md](superpowers/specs/2026-11-12-firebase-and-ads-kmp-design.md).

## Modules

| Module | Platform | What |
|---|---|---|
| `:core:consent:shared` | KMP | `ConsentState`, the "resolved" rule, `ConsentPort`, startup gathering |
| `:core:consent:google` | Android | UMP SDK implementation of `ConsentPort` |
| `:core:firebase:shared` | KMP | `RemoteValues` engine (typed reads, fallbacks), ports, Koin module |
| `:core:firebase:google` | Android | Firebase SDK: Remote Config source, Analytics, Crashlytics |
| `:core:firebase:crashlytics` | iOS | Kotlin crash hook over CrashKiOS. The iOS app plugin adds it only when `firebase.enabled` is not `false` |
| `ios/WhitelabelConsent` | iOS | Swift target `WhitelabelConsent`: UMP |
| `ios/WhitelabelFirebase` | iOS | Swift target `WhitelabelFirebase`: the Firebase ports and `FirebaseService`. The XcodeGen template `WhitelabelFirebaseApp` links both targets into the app and adds the dSYM upload |

`RemoteValues` lives in `:core:domain` with a defaults-only implementation, so features can read
values whether or not the app turned Firebase on. Without Firebase they just return their defaults.

The Firebase Android BoM is pinned at 34.19.0: 35.x pulls a measurement library built with
Kotlin 2.4 metadata, which this build's Kotlin 2.2.20 can't read. Upgrade both together.

## Turning it on

**1. `app.properties`:**

| `firebase.enabled` | Effect |
|---|---|
| `false` (default) | No Firebase: no SDK in the Android build, no CrashKiOS in the iOS framework |
| `true` | Every build type. A missing config file or AdMob app ID **fails the build** |
| `debug` | Debug builds only. Debug is real only with the platform's config file **and** its AdMob app ID; if either is missing, Debug runs on a fake Firebase. A Release build has no Firebase on Android and never starts it on iOS |

The AdMob app IDs are also keys in `app.properties` (the ads plan reuses them):

| Key | Format | Rule |
|---|---|---|
| `ads.android.appId` | `ca-app-pub-<16 digits>~<10 digits>` | Required with `firebase.enabled=true`; checked for format whenever present (even with `false`) |
| `ads.ios.appId` | same | Same, for iOS |

Why: Google's consent SDK (UMP) belongs to AdMob. It needs the app's AdMob app ID, and the consent
form is configured in AdMob. Without the ID, consent fails at every launch and nothing is ever
collected. Failures read `apps/<name>: firebase.enabled=true needs ads.android.appId (the AdMob app ID;
Google's consent SDK requires it)` (Android build) and the same with `ads.ios.appId` (from
`generateIosConfig`). Android: the application plugin sets the `admobAppId` manifest placeholder
(empty without an ID), which `:core:consent:google`'s manifest turns into the
`com.google.android.gms.ads.APPLICATION_ID` meta-data. iOS: `generateIosConfig` writes
`WL_ADMOB_APP_ID`, and Info.plist maps it to `GADApplicationIdentifier`.

**AdMob console steps** (the owner's, once per app):
1. Create an AdMob account (admob.google.com).
2. Add the Android app and the iOS app (an app that isn't published yet can be added by name).
3. Copy each app ID (`ca-app-pub-...~...`, with a tilde) into `ads.android.appId` / `ads.ios.appId`.
4. Privacy & messaging > GDPR: create and **publish** a message for the apps. Without a published
   message UMP never shows a form.
5. To test the form outside the EEA, UMP's debug geography needs the test device's hashed ID, which
   UMP logs when it runs (look for it in logcat or the Xcode console).

**2. Config files** from the Firebase console (one Android app and one iOS app per application ID;
Debug IDs end in `.debug`, so register that ID for a `debug` app):

| Platform | Put it at | Missing with `true` fails with |
|---|---|---|
| Android | `apps/<name>/google-services.json` | `apps/<name>: firebase.enabled=true needs google-services.json` |
| iOS | `ios/<App>/<App>/GoogleService-Info.plist` | `apps/<name>: firebase.enabled=true needs GoogleService-Info.plist` (from `generateIosConfig`) |

Showcase has no AdMob IDs yet (the owner adds them), so showcase Debug runs on the fake Firebase even
with both files present, until `ads.android.appId` and `ads.ios.appId` are set.

Showcase's two files are gitignored, because this repo is public. Without them showcase Debug runs on
the fake Firebase (its remote note reads "from the fake"). To check against the real Firebase, drop
your own two files in those paths (`apps/showcase/google-services.json` and
`ios/Showcase/Showcase/GoogleService-Info.plist`) and keep them out of commits.

**3. Android, in the app's definition:**

```kotlin
fun globexDefinition(): AppDefinition = appDefinition {
    firebase()          // RemoteValues, Analytics and CrashReporter from Firebase
    // ...
}
```

Don't combine `firebase()` with `analytics(...)` or `crashReporter(...)`: the definition fails with
`firebase() replaces analytics() and crashReporter(); use one`. `firebase()` without
`firebase.enabled` fails at startup with `firebase() needs firebase.enabled=true or debug in
app.properties`, and the reverse with `firebase.enabled is set but the app definition doesn't call
firebase()`.

**4. iOS, in the app's `@main`:**

```swift
import WhitelabelFirebase

RootView(definition: AppDefinition(style: .material, services: [FirebaseService()]) { … })
```

- The app's `project.yml` includes `WhitelabelConsent.yml` and `WhitelabelFirebase.yml`, uses the
  template `templates: [WhitelabelFirebaseApp]` on the app target and adds `-ObjC` to
  `OTHER_LDFLAGS` (copy `ios/Showcase/project.yml`).
- The app's `Info.plist` must start collection off until consent. All six keys, each Boolean `NO`:
  `FIREBASE_ANALYTICS_COLLECTION_ENABLED`, `FirebaseCrashlyticsCollectionEnabled`,
  `GOOGLE_ANALYTICS_DEFAULT_ALLOW_ANALYTICS_STORAGE`, `GOOGLE_ANALYTICS_DEFAULT_ALLOW_AD_STORAGE`,
  `GOOGLE_ANALYTICS_DEFAULT_ALLOW_AD_USER_DATA`,
  `GOOGLE_ANALYTICS_DEFAULT_ALLOW_AD_PERSONALIZATION_SIGNALS`. Also map `WLFirebase` to
  `$(WL_FIREBASE)`, and add `GADApplicationIdentifier` = `$(WL_ADMOB_APP_ID)`. Without the keys, the app stops at launch with
  `Firebase is on, but Info.plist doesn't start collection off until consent; set these to NO (Boolean): <the keys>`.
- Without `GADApplicationIdentifier` (missing or empty), `FirebaseService` in `on` mode also stops the
  app at launch, naming the key.
- A `FirebaseService` in an app whose `WL_FIREBASE` is `off` in Debug fails at launch (Android's
  rule). With `firebase.enabled=debug` a Release build is legitimately off.
- The framework module gets `:core:firebase:crashlytics` and the CrashKiOS link plugin from
  `whitelabel.ios.app` by itself. Showcase's `iosApp { firebaseConfigFile.set(...) }` points at its
  plist; the default is `<project dir>/<project dir name>/GoogleService-Info.plist`.

**5. In an app's own repo,** add the two Gradle plugins to the root `build.gradle.kts`, declared but
not applied, next to the other aliases (the convention plugins apply them by id):

```kotlin
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
```

The foundation's own root file also has `alias(libs.plugins.crashkios.crashlyticslink) apply false`,
which a repo needs only when it builds the iOS framework. Copy `build.gradle.kts` from the foundation
(or `fixtures/standalone-app/build.gradle.kts`, the Android-only version). The build says
"Plugin with id … not found" until the lines are there.

## Remote values

```kotlin
val values = getKoin().get<RemoteValues>()          // from a ViewModel: inject RemoteValues
val title = values.string("home_title", "Welcome")   // the default is always yours
```

- `long`, `double`, `string`, `boolean` and `json(key, default, serializer)`. A missing key, an empty
  value, a value of the wrong type or garbled JSON returns the default and never throws.
- `changes` emits after each activation that changed a value.
- The app fetches and activates once per launch in the background, off the startup path. Later
  launches start from the cached values. The minimum fetch interval is 0 s in Debug and 12 h in
  Release, so a console change shows at once in Debug and within a day in Release.
- `RemoteValues` is separate from the backend's `app/config` feature flags.

## Consent

Where the law requires it (EEA/UK, as UMP decides), the user is asked once per launch, after the
first screen appears and never over the splash. Elsewhere nothing shows.

- Nothing is collected before consent is resolved: events are dropped, not queued.
- A user who declines is still counted, without identifiers (analytics storage denied).
- Crash reports that Crashlytics stored before consent are dropped. Reports from consented sessions
  are kept across launches.
- If the consent request fails or the form is dismissed, nothing is collected that launch and it is
  asked again next launch.
- The collection flags persist across launches. A cold start with a consent given earlier collects
  before this launch's re-check finishes. A change made in the privacy options form, a withdrawal
  included, applies as soon as the form closes: the app re-reads UMP's answer then.
- A Debug build without the DebugView switch turns collection off at startup, before consent, so
  flags left on by an earlier `analytics-debug` / `-analyticsDebug` run don't keep it collecting.

Put `PrivacyChoicesRow()` in the app's own settings section, so users can reopen the form. It shows
only while UMP says privacy options are required, and draws nothing in an app without Firebase:

```kotlin
// R.string.my_settings_privacy is the app's own string (showcase uses R.string.showcase_settings_firebase).
settings(SettingsOptions(sections = listOf(SettingsSection(R.string.my_settings_privacy) {
    PrivacyChoicesRow()
})))
```

On iOS it is `PrivacyChoicesRow()` in a `SettingsSection` (see `ios/Showcase/Showcase/ShowcaseApp.swift`).

## Debug switches

Debug builds only. A release build ignores them.

| Switch | Android marker under `files/debug/` | iOS launch argument |
|---|---|---|
| Fake Firebase (in-memory values, logging analytics) | `fake-firebase` | `-fakeFirebase` |
| Collection on in Debug, events to DebugView | `analytics-debug` | `-analyticsDebug` (also Firebase's debug mode) |
| Behave as in the EEA and reset the stored consent (the form shows on every launch) | `consent-eea` | `-consentEEA` |
| What the fake consent answers | `fake-consent-<obtained\|declined\|failed\|notRequired>` | `-fakeConsent <state>` |

Fake mode never shows a real consent form: it answers from the `fake-consent` switch (default
`obtained`).

Android markers are empty files, written without a rebuild:

```sh
adb shell "run-as <applicationId> sh -c 'mkdir -p files/debug && touch files/debug/fake-firebase'"
adb shell run-as <applicationId> rm files/debug/fake-firebase     # switch it off again
```

Keep the outer double quotes: `adb shell` joins its arguments, so without them `mkdir` gets no
argument. `<applicationId>` is the debug one, e.g. `com.whitelabel.showcase.debug`. Restart the app after
changing a marker. iOS arguments go in the Xcode scheme, or on the command line:

```sh
xcrun simctl launch --console-pty booted <bundle id> -fakeFirebase -fakeConsent declined
```

## DebugView

Debug collection is off by default so test sessions don't pollute the numbers. To see events live in
the console's DebugView:
- **Android:** touch the `analytics-debug` marker, and run
  `adb shell setprop debug.firebase.analytics.app <applicationId>` (it resets on reboot).
- **iOS:** launch with `-analyticsDebug`.

Without the switch, no event appears in DebugView. Release collects normally, once consent allows.

## Crashlytics

- **Android:** the Crashlytics plugin uploads the R8 mapping with a release build.
- **iOS:** an Xcode build phase uploads the dSYMs when `WL_FIREBASE` is `on`. Fake and off builds do
  nothing. The script comes from the firebase-ios-sdk package checkout, and the build only warns if
  it isn't there, so check that a real upload shows in the console (see the manual checklist).
- Kotlin exceptions reach Crashlytics with their Kotlin frames on iOS, through CrashKiOS. Both
  uncaught ones and `CrashReporter.record(throwable)`.
- Each uncaught crash is reported once. On Android with Firebase on, the shell's uncaught-exception
  handler doesn't record it through `CrashReporter` (that would add a non-fatal copy): it chains to
  Crashlytics' own handler, which reports the fatal crash. Off and fake builds record it through the
  app's `CrashReporter` (the log).
- The Android release check (`scripts/verify-apps.sh release`) fails if a release dex contains
  Firebase while `firebase.enabled` is not `true`.

## Checking it

Unit tests and builds need no Firebase project. The manual checks with a real one are in
[the checklist](superpowers/checklists/2026-11-12-firebase-manual.md).
