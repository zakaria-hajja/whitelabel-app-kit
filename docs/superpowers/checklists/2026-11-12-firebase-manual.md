# Firebase and consent: manual checklist (2026-11-12)

Checks that need a real Firebase project, so no automated test can do them. Run them on both
platforms with showcase Debug. **Not run yet**: the owner hasn't provided showcase's config files.
Every result is blank; fill them in and commit them as `docs(firebase): record the manual firebase checks`.

Background: [firebase.md](../../firebase.md). Spec: section 7 of
[the design](../specs/2026-11-12-firebase-and-ads-kmp-design.md). Items 5 and 6 of the spec belong to
the ads branch and aren't here.

## Setup

1. **Firebase project** (the owner's), with two apps:
   - Android, application ID `com.whitelabel.showcase.debug`
   - iOS, bundle ID `com.whitelabel.showcase.debug`
2. **AdMob** (consent needs it): create an AdMob account, add the Android and iOS apps, copy their
   app IDs into `ads.android.appId` and `ads.ios.appId` in `apps/showcase/app.properties`, and publish a GDPR message under
   Privacy & messaging. Without the IDs showcase Debug stays on the fake Firebase.
3. **Config files** (both are gitignored, never commit them):

   | Platform | Download | Put it at |
   |---|---|---|
   | Android | `google-services.json` | `apps/showcase/google-services.json` |
   | iOS | `GoogleService-Info.plist` | `ios/Showcase/Showcase/GoogleService-Info.plist` |

4. **Firebase console:**
   - Remote Config: add the parameter `showcase_remote_note` (string, e.g. `from the console`) and
     publish it.
   - Analytics and Crashlytics are enabled for the project (Crashlytics: open the dashboard once so it
     starts listening).
   - DebugView: Analytics > DebugView (the device appears once debug mode is on).
5. **Build and run** (Debug, with the config files in place the app runs on the real Firebase, not the
   fake):
   - Android: `./gradlew :apps:showcase:assembleDebug -Pwhitelabel.mockApi=true`, install the APK and
     launch it (the mock backend lets you sign in without a server).
   - iOS: `./gradlew :apps:showcase:shared:generateIosConfig`, then
     `(cd ios/Showcase && xcodegen) && open ios/Showcase/Showcase.xcodeproj`, and run on the
     "iPhone 18 Pro Max" simulator. Add `-mockApi` to the scheme's arguments to sign in.
6. Android debug switches are written with
   `adb shell "run-as com.whitelabel.showcase.debug sh -c 'mkdir -p files/debug && touch files/debug/<marker>'"`
   and removed with `adb shell run-as com.whitelabel.showcase.debug rm files/debug/<marker>`.
   iOS ones are launch arguments. Restart the app after each change.

Tester: ____________  Date: ____________  Android device/emulator: ____________  iOS device/simulator: ____________  Commit: ____________

## 1. A Remote Config value set in the console arrives after relaunch

Settings > Firebase > "Remote note" shows `default` until a value is fetched.

| Step | Android | iOS |
|---|---|---|
| Launch once: the note reads `default` or the old value | | |
| Change `showcase_remote_note` in the console and publish | | |
| Relaunch (Debug fetches at once: interval 0 s): the note shows the new value | | |
| Result (pass/fail, notes) | | |

## 2. Events appear in DebugView with the debug switch on, and not without it

| Step | Android | iOS |
|---|---|---|
| Switch: marker `analytics-debug` plus `adb shell setprop debug.firebase.analytics.app com.whitelabel.showcase.debug` / argument `-analyticsDebug` | | |
| Navigate between tabs: `screen_view` events appear in DebugView | | |
| Switch off (remove the marker / drop the argument) and relaunch: no events appear in DebugView | | |
| Result | | |

## 3. A forced test crash appears in Crashlytics

Showcase has no crash button. Add a temporary `throw RuntimeException("manual crash check")` in a
click handler of a Debug build (Kotlin, for the Kotlin-frames check on iOS), run it with
`analytics-debug` / `-analyticsDebug` (collection is off in Debug otherwise), and **do not commit it**.
Relaunch after the crash: Crashlytics sends the report on the next launch.

| Step | Android | iOS |
|---|---|---|
| The crash report appears in the Crashlytics console | | |
| iOS only: the report shows the Kotlin frames (the Kotlin file and function names), not only native ones | n/a | |
| Result | | |

## 4. With the debug geography set to EEA, the UMP form appears on first launch

Needs a fresh install (uninstall, or clear the app data) so the first launch is a real one.

| Step | Android | iOS |
|---|---|---|
| Fresh install with the switch: marker `consent-eea` (touch it before the first launch, after `adb install`; run-as needs the app installed) / argument `-consentEEA`, and `analytics-debug` / `-analyticsDebug` so events are visible | | |
| The form appears on the first launch, after the first screen (not over the splash) | | |
| Before answering, no event reaches DebugView | | |
| After answering (consent): events start; Settings > Firebase shows the "Privacy choices" row | | |
| Repeat with "Do not consent": the app works, events appear without identifiers, no crash | | |
| Without the EEA switch (fresh install): nothing appears and no "Privacy choices" row | | |
| Result | | |

## 5. The remote note from the fake

Task 11's smoke check couldn't get past sign-in, so this is unverified. Use the fake, not the real
config: temporarily move the config files away (or launch showcase without them) so Debug runs on the
fake.

| Step | Android | iOS |
|---|---|---|
| Launch with the marker `fake-firebase` / argument `-fakeFirebase` (and the mock backend to sign in) | | |
| Settings > Firebase > "Remote note" reads `from the fake` | | |
| No consent form shows in fake mode (default `fake-consent-obtained` / `-fakeConsent obtained`) | | |
| Result | | |

## 6. A real dSYM upload appears in Crashlytics (iOS)

| Step | iOS |
|---|---|
| Build Debug with the plist in place; the build log shows the "Upload dSYMs to Crashlytics" phase running without a warning (`the dSYMs were not uploaded` or `the Crashlytics dSYM upload failed`) | |
| In the console, Crashlytics > dSYMs lists the uploaded dSYM for `com.whitelabel.showcase.debug` | |
| The crash from item 3 is symbolicated (function names, not addresses) | |
| Result | |

## Result

| Platform | Items passed | Open issues |
|---|---|---|
| Android | | |
| iOS | | |
