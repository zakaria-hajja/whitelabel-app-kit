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
  - `AppValueRow`, `AppToggleRow`, `AppChoiceSheet`;
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

### Debug controls (Debug builds)

| Launch argument / link | Effect |
|---|---|
| `-mockApi` | run on the in-memory fake backend |
| `-freshInstall` | start as a new install: no stores, no Keychain key, device language |
| `-fakeFeatures catalog=false,profile=true` | the fake's remote flags |
| `-fakeConfigDelayMs 5000` | the fake answers the config that late |
| `<scheme>://_fake/offline`, `online`, `expire-tokens`, `revoke-sessions` | steer the running fake |

UI tests launch with `-mockApi -freshInstall -AppleLanguages (en)`. iOS reads `AppleLanguages`
before any app code runs, so `-freshInstall` alone can't undo a language an earlier test picked.
Robots (`ShowcaseUITests/Robots`) say what the user does, as on Android.

## Platform pieces (Swift)

- **`KeychainTokenCipher`:** AES-GCM via CryptoKit, with the key in the Keychain (after first
  unlock, this device only, not synced). Without the key, as after a restore, the session reads as
  signed out.
- **`IosLanguageRepository`:** the app's language choice, written to the app's `AppleLanguages`.
  iOS applies it at the next launch (`appliesImmediately` is false, so settings shows a note).
- **Stores:** in Application Support, `datastore/<name>.json`.
