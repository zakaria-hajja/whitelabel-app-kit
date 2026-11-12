# iOS

Every app ships on iOS with the same logic as on Android: rules, data and ViewModels are shared
Kotlin, and the screens are SwiftUI. The Swift half of the foundation lives in `ios/`. Design:
[2026-11-11-ios-starter-design.md](superpowers/specs/2026-11-11-ios-starter-design.md).

## Setup (macOS)

- Xcode, selected: `sudo xcode-select -s /Applications/Xcode.app/Contents/Developer`, then open it
  once (or `sudo xcodebuild -runFirstLaunch`) and install an iOS simulator runtime.
- XcodeGen: `brew install xcodegen`. Xcode projects are generated from `project.yml` and not
  committed.
- Run showcase:
  ```sh
  ./gradlew :apps:showcase:shared:generateIosConfig      # app.properties → ios/Showcase/Config/*.xcconfig
  (cd ios/Showcase && xcodegen) && open ios/Showcase/Showcase.xcodeproj
  ```
- `scripts/check-ios.sh` is the iOS gate:
  - WhitelabelKit's tests on a simulator;
  - a Release build that must not contain the fake backend.

  While the owner's UI-test pause lasts, the script skips showcase's `ShowcaseUITests`
  (`-skip-testing:ShowcaseUITests`). Remove that flag when the pause ends.

  The pre-push hook runs it on macOS when a push touches `ios/` or shared Kotlin. GitHub CI stays
  Android-only.

## How the pieces fit

| Piece | Where | What |
|---|---|---|
| The app's framework | `apps/<app>/shared` (KMP) | Exports the shared modules as one static framework named **`Shared`** (every app uses that name), with SKIE: flows become async sequences, sealed types Swift enums, suspend functions `async` |
| Entry point | `:core:ios` (`SharedGraph`) | `SharedGraph.start(platform:definition:appModules:useMockApi:)` once, then one typed accessor per shared ViewModel (`settingsViewModel()`, `resetPasswordViewModel(token:)`, …) |
| Fake backend | `:core:ios:mock` (`IosMock`) | Linked only with `-Pwhitelabel.mockApi=true`, which only the Xcode Debug configuration passes; call `IosMock.shared.enable()` before starting |
| WhitelabelKit | `ios/WhitelabelKit` | A static framework target every app's `project.yml` includes (`WhitelabelKit.yml`): the bridge (`SharedModel`, `UiTextView`), the platform pieces, the design system, the shell (`AppDefinition`, `RootView`) and the foundation's feature screens |
| App identity | `generateIosConfig` (`whitelabel.ios.app`) | Writes `Debug`/`Release.generated.xcconfig` from `app.properties`: bundle id (`.debug` in Debug), name, version, API URLs (`10.0.2.2` → `localhost`), tenant, deep-link scheme, defaults. `Info.plist` maps them to `WL*` keys, which `PlatformFactory` reads |

**Build.**
- WhitelabelKit's build phase runs Gradle's `embedAndSignAppleFrameworkForXcode` for the app's
  framework module (set by `WL_SHARED_PROJECT`), so the kit compiles against `Shared`.
- The app's own build phase runs it again, which also copies the shared strings into the app.
  Without that, messages are missing at runtime.
- Only Apple-silicon simulators are built (`EXCLUDED_ARCHS[sdk=iphonesimulator*] = x86_64`).

## The bridge

- **`SharedModel<State, Action>`** (`@Observable`, main actor) holds a shared ViewModel through a
  `ViewModelHolder`. `state` follows its `StateFlow`, `send(_:)` forwards an action, and releasing
  the model clears the ViewModel. Screens never compute state themselves.
- **`ScreenModel { … }`** builds a screen's `SharedModel` on first use. Keep it in `@State`:
  SwiftUI re-evaluates a `@State` initial value on every rebuild, so a `SharedModel` built there
  would create (and clear) a Kotlin ViewModel each time.
- **Messages:** `UiTextView(text)` or `await resolve(text)` give a ViewModel's `UiText` in the
  language the app launched in. `Texts` pins it at launch, because a language picked in the app
  applies at the next launch and the Swift strings wait until then too.
- **`actionBanner($item)`:** a message with one action, for events a screen handles itself
  ("Deleted · Undo"). Set a `BannerItem(text:actionTitle:action:)`. It shows for
  `MessageBanner.seconds`, the button runs the action and hides it, and a newer item replaces it.
  Identifiers for UI tests: `banner.text` and `banner.action`.
- **Swift gotchas:**
  - Write `Swift.Error`: the module exports a Kotlin `Error` type.
  - SKIE turns Kotlin enums into Swift enums. Pass one back with `.toKotlinEnum()` where a Kotlin
    interface is expected.
  - A Kotlin exception Swift can catch is declared `@Throws`; any other one aborts the app.
  - In a file that imports `Shared`, write `@SwiftUI.Environment(…)`: the module exports a Kotlin
    `Environment` (the build environment) too.

## Design system (`WhitelabelKit/DesignSystem`)

- **Packs:**
  - `StylePack.material` is standard iOS styling, tinted with the brand.
  - `StylePack.gallery` is hairlines, small radii, roomy controls and soft motion.

  Their corners, sizing, surfaces, motion (springs as `response` = 2π/√stiffness), haptics and
  spacing are copied from the Kotlin packs, with comments naming each source. `StylePackTests` pins
  them, and Kotlin wins.
- **Brand:**
  - Colors come from the app's asset catalog: `BrandPrimary`, `BrandOnPrimary`, … mirroring
    Android's `brand_light_*`/`brand_dark_*`, with light and dark appearances.
  - Fonts come by PostScript name from Info.plist `WLBrandDisplayFont`/`WLBrandBodyFont`, listed in
    `UIAppFonts`.
  - A missing color or font falls back to the system's.
- **Using them:** apply with `.appStyle(AppStyle(pack:))`; Reduce Motion swaps in the pack's
  reduced motion. Views read `@Environment(\.appStyle)`.
- **Components** are native controls styled by the pack:
  - `PrimaryButtonStyle`/`SecondaryButtonStyle`/`TextButtonStyle`, `AppCard`, `AppTextField`
    (announces its error);
  - `AppValueRow` (inline or stacked, per the pack), `AppToggleRow`, `AppChoiceSheet`, `AppSectionHeader`;
  - `LoadingView`, `EmptyStateView`, `ErrorStateView`, `.appConfirm(…)`.

  Labels wrap at every Dynamic Type size.
- **Kit labels:** the kit's own labels live in `Resources/WhitelabelKit.xcstrings` (en, fr), which
  the app target includes. A static framework's resources don't ship by themselves.
- **Catalog:** `DesignSystemCatalog` shows every component in both packs, light or dark. Showcase
  opens it in Debug with `-designCatalog` (`-catalogDark` for dark). Gallery there uses the sample
  assets in `Resources/Preview`: Munch's colors from `tokens.json`, Instrument Serif and Geist (OFL).
  Compare it with Munch's `docs/design/design.html` before an app adopts the pack.

## The app definition and the shell (`WhitelabelKit/Shell`)

An app's `@main` is a few lines, the Swift twin of Android's `appDefinition { }`:

```swift
@main struct ShowcaseApp: App {
    var body: some Scene {
        WindowGroup {
            RootView(definition: AppDefinition(style: .material) {
                Settings()
            })
        }
    }
}
```

- **`AppDefinition(style:startTab:modules:) { features }`**: the style pack, the features in tab
  order, an optional start tab (a route id such as `SettingsRoutes.shared.ROOT`) and the app's own
  Koin modules. Its `spec` is the shared `DefinitionSpec`. The rules (one gate, sign-in features
  need `Auth()`, at least one tab…) are checked in Kotlin, and a broken definition stops the app at
  launch with the rule's message, as on Android.
- **Services:** `AppDefinition(style:startTab:modules:services:)` also takes `services`, a list of
  `AppService`s that install platform pieces before the graph starts. `FirebaseService()`
  (`WhitelabelFirebase`) is the Swift twin of Android's `firebase()`; see [firebase.md](firebase.md).
  A service in an app whose `WL_FIREBASE` setting disagrees stops the app at launch.
- **`RootView`** starts the shared graph once and shows a splash until the shared `ShellViewModel`
  decides the tabs. It waits at most `CONFIG_WAIT_MS` (2 s) for the remote config, then uses the
  build-time defaults. Then it shows the gate's screen (signed out, with `Auth()`) or the tabs: a
  native `TabView` with one `NavigationStack` per visible tab. It applies the pack and the theme.
  As the host of unit tests it stays empty.
- **Links:** as on Android, an incoming link is first kept in the shell model, then decided once
  the shell shows content (tabs decided and, with a gate, the session known). Swift finds the
  enabled feature that owns it (`Feature.destination(forLink:)`; a feature its flag switched off
  owns none). The shared `deepLinkAction` decides whether it opens, waits for sign-in or is
  dropped. A gate's link (reset password) opens on the gate's own stack.
- **`Navigator`** (`@Environment(Navigator.self)`): the selected tab, each tab's stack and the
  gate's stack. A screen calls `push(Destination(route, args:))` to open another screen. With a
  gate, signing in or out resets it, so the next user starts clean.
- **`messageBanner(state.message) { send(OnMessageShown) }`**: a ViewModel's one-off message at the
  bottom for 3 s (Android's snackbar). A newer message replaces it.

### Features, and an app's own screens

A feature is a type that conforms to `Feature`:

| Member | What |
|---|---|
| `spec: FeatureSpec` | id, flag key, tab route, `requiresSignIn`, `isGate`, `requiresBackend`: what the shared shell model decides on |
| `tab() -> TabItem?` | its tab: route, localized title, SF Symbol |
| `routes(route, args) -> AnyView?` | the screen for a route it owns, else nil; its tab root is called with the tab's route |
| `destination(forLink:)` | optional: where a link of the app's scheme leads |
| `gateRoot()` | the gate only: its signed-out screen |

An app's own iOS screens plug in the same way: a `Feature` in the app's Xcode target, listed in
its `AppDefinition`. Its screens get their shared ViewModel through a typed accessor and a
`ScreenModel`, and build from the design system. Route ids are shared `<Name>Routes` constants, so
both platforms name screens alike.

**Settings** (`Settings(options:)`): theme, language, notifications (signed in), about, legal
links (opened in Safari) and sign out. `SettingsOptions` mirrors Android's (`showNotifications`,
`languages`, `sections`, `aboutNote`). An app's own `SettingsSection(title:) { rows }` goes above
the shared ones. The diagnostics row is Android-only. A language picked in the app applies at the
next launch, and the screen says so (a shared string).

**Auth** (`Auth(options:)`, the gate): sign-in, registration, forgot password and the reset link
(`<scheme>://reset-password?token=…`), on the shared ViewModels. `AuthOptions` mirrors Android's
(`allowRegistration`, `allowPasswordReset`). Its screens push onto the gate's own stack. The
title's app name comes from the brand (`@Environment(\.appBrand)`).

**Home** (`Home(shortcuts:)`): the greeting for the signed-in user (shared), pull to refresh,
and the app's `HomeShortcut(title:systemImage:route:)`s. A shortcut opens the tab with that root
route, and is hidden when that tab is missing or switched off. Needs `Auth()`.

**Profile** (`Profile(allowPasswordChange:)`, flag key `profile`): the email, the names, and a
password change that signs out the other devices. Needs `Auth()`.

**Catalog** (`Catalog(categories:pageSize:defaultSort:)`, flag key `catalog`): search
(`.searchable`), category chips, the sort menu, paging at the end of the list, pull to refresh, and
the detail by id, also from `<scheme>://catalog/<id>`. The options are checked in Kotlin
(`CatalogOptions`): an invalid one stops the app at launch, as on Android. Needs `Auth()`.

Showcase on iOS has the same features, order and start tab as on Android:
`Auth()`, `Home(…)`, `Catalog(…)`, `Profile()`, `Settings()`, start tab home.

### Debug controls (Debug builds)

Debug builds always show **Continue as guest (debug)** on the sign-in screen. It signs in as
`alice@shared.test`, on the fake or on a backend seeded with `npm run db:seed`
(`DebugControls` hands the account to `SharedGraph.useDebugGuest`; Release never does).

| Launch argument / link | Effect |
|---|---|
| `-mockApi` | run on the in-memory fake backend |
| `-freshInstall` | start as a new install: no stores, no Keychain key, device language, no fake sessions |
| `-dropTokenKey` | delete only the Keychain key, as after a reinstall or a restored backup: the session reads as signed out |
| `-fakeResetToken t1` | the fake issues reset token `t1`, already issued for `alice@shared.test` |
| `-fakeFeatures catalog=false,profile=true` | the fake's remote flags |
| `-fakeConfigDelayMs 5000` | the fake answers the config that late |
| `-fakeFirebase` | Firebase runs as the in-memory fake (needs `FirebaseService()`) |
| `-analyticsDebug` | analytics and crash collection on in Debug, events to Firebase's DebugView |
| `-consentEEA` | the consent form behaves as in the EEA, and the stored consent is reset first |
| `-consentTestDevice <ID>` | registers this simulator or device as a UMP test device (UMP logs the ID); needed for `-consentEEA` to take effect |
| `-fakeConsent obtained\|declined\|failed\|notRequired` | what the fake consent answers (default `obtained`) |
| `-messageSeconds 15` | one-off messages and banners stay that long |
| `-notificationAccess notAskedYet\|granted\|blocked` | the notification permission reports that status; asking grants without the system alert, which UI tests can't answer |
| `<scheme>://_fake/offline`, `online`, `expire-tokens`, `revoke-sessions` | steer the running fake |
| `<scheme>://_fake/log` | show the fake's recent requests in the `debug.fakeLog` probe |

Debug probes (invisible, for UI tests): `debug.colorScheme` (the applied scheme),
`debug.lastLink` (the last link the app received; `openLink` waits for it) and `debug.fakeLog`.

In Debug the fake keeps its sessions in Caches across launches, as a real server would, so a
relaunch stays signed in. Accounts registered during a run are not kept.

UI tests launch with `-mockApi -freshInstall -AppleLanguages (en)`. iOS reads `AppleLanguages`
before any app code runs, so `-freshInstall` alone can't undo a language an earlier test picked.
Robots (`ShowcaseUITests/Robots`) say what the user does, as on Android. Every test in
Android's `ShowcaseE2ETest` and `CleanStartTest` has an iOS counterpart in `ShowcaseUITests`
(`AuthUITests`, `CatalogUITests`, `ProfileUITests`, `CleanStartUITests`); the iOS suite adds
settings, home, shell and design-catalog tests. Two UI-test traps:
- open a link with `openLink` (`XCUIDevice.shared.system.open`). `XCUIApplication.open(url)`
  relaunches the app with the same arguments, `-freshInstall` included;
- after a successful sign-in, iOS offers to save the password. The sign-in robot answers
  "Not Now", or the sheet swallows the next tap.

## Firebase and consent targets

Two more Swift targets sit beside `WhitelabelKit.yml`, and an app includes them through XcodeGen only
when it uses Firebase: `WhitelabelConsent` (UMP) and `WhitelabelFirebase` (Remote Config, Analytics,
Crashlytics, `FirebaseService`). `WhitelabelKit` never imports Firebase. The app target uses the
`WhitelabelFirebaseApp` template, which links both and adds the Crashlytics dSYM upload. The
Swift packages link in Release too (SPM can't drop them per configuration), but Firebase only starts
where `WL_FIREBASE` is `on`. The setup steps and the Info.plist keys are in [firebase.md](firebase.md). The app's Info.plist
also needs `GADApplicationIdentifier` = `$(WL_ADMOB_APP_ID)` (`generateIosConfig` writes
`WL_ADMOB_APP_ID` from `ads.ios.appId`): Google's consent SDK needs the AdMob app ID, and
`FirebaseService` stops the app at launch without it.

The `whitelabel.ios.app` plugin exports `:core:consent:shared` and `:core:firebase:shared` from every
app's `Shared` framework, with or without Firebase: they are pure Kotlin, and WhitelabelKit's Swift
names their types (`Consent` in `PrivacyChoicesRow`). Only an app with Firebase also gets
`:core:firebase:crashlytics` (the Kotlin crash hook over CrashKiOS) and CrashKiOS' link plugin. An
app's own export list names none of them.

## Platform pieces (Swift)

- **`KeychainTokenCipher`:** AES-GCM via CryptoKit, with the key in the Keychain (after first
  unlock, this device only, not synced). Without the key, as after a restore, the session reads as
  signed out.
- **`IosLanguageRepository`:** the app's language choice, written to the app's `AppleLanguages`.
  iOS applies it at the next launch (`appliesImmediately` is false, so settings shows a note).
- **Stores:** in Application Support, `datastore/<name>.json`.
- **Reminders:** `iosRemindersModule()` (Kotlin, `:core:reminders:shared`) in the app's modules;
  see [reminders.md](reminders.md#ios).
- **`NotificationPermission`** (`@Observable`): `status` is `.granted`, `.notAskedYet` or
  `.blocked` (iOS asks once). `request()` shows the system alert, `openSettings()` opens the app's
  notification settings, and `.notificationPermissionRefresh(permission)` re-reads it when the app
  returns to the foreground.
- **`ReminderNotifications`:** shows reminders while the app is open, and opens a tapped reminder's
  deep link through the shell (so it waits until content shows). `RootView` installs it. Call
  `ReminderNotifications.shared.install()` in the app's `init()` too, so a tap that launches the
  app isn't missed.
