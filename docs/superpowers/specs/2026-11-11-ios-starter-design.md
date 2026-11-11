# iOS Starter (step 5) — Design

Status: draft (2026-11-11); builder check done, owner's answers in; waiting for the owner's approval.
Parent design: `2026-11-11-ios-kmp-stack-design.md` (approach A: KMP shared up to the ViewModels,
SwiftUI on iOS). This document is its step 5, and it replaces that design's §3.2 where they differ.

## 0. Intent and the why

Every whitelabel app now ships on Android and iOS. Steps 1–4 (#23, #25, #26, #28) moved the
foundation's logic and ViewModels into Kotlin Multiplatform. `ShowcaseShared.framework` builds,
carries the shared strings, and every shared ViewModel resolves on iOS.

The iOS starter is the Swift half of the foundation's UI side. With it, an app gets on iOS what it
gets on Android from `:core:shell`, `:core:designsystem` and the feature UI modules: an app shell,
a design system with style packs, branding from the app's own config, and screens for the shared
features. Munch (step 7) is the first real app on it. Every later app reuses it.

### What success looks like

- Showcase runs on the iOS simulator with sign-in, home, catalog, profile and settings. It runs
  against the same in-memory fake backend Android uses, and its main flows have XCUITests.
- A new app's iOS project consists of the app's identity (from its existing `app.properties`), its
  brand assets, and a short Swift feature list mirroring its Android `appDefinition { … }`.
- An app on the Gallery pack looks like Munch's `docs/design/design.html` and behaves like an iOS
  app.
- Changing a shared ViewModel and its Swift screen happens in one PR, and can't drift apart.

### Goals

- **A shared shell model:** tab resolution, the sign-in gate and deep-link policy move to Kotlin.
- **The Swift bridge:** SKIE, plus one small helper and typed ViewModel accessors.
- **A Kotlin entry point Swift calls** to start the graph with Swift-implemented platform pieces.
- **Those platform pieces:** a Keychain + CryptoKit `TokenCipher`, a `LanguageRepository` and store
  paths.
- **Design system:** the Material and Gallery packs, light and dark.
- **Branding:** from `app.properties` and the app's assets.
- **Feature screens at parity, in order:** settings (with the shell), then home, profile, catalog,
  authentication.
- **Showcase on iOS** with E2E tests, a local iOS check script, and docs.

### Non-goals

- Munch on iOS (step 7) and Munch's own features.
- Reminders and notifications on iOS (`UNUserNotificationCenter`), Firebase, ads, and iCloud
  backup policy. They arrive with the apps that need them, as in the parent design's step 7.
- The diagnostics screen on iOS. Settings hides its row on iOS for now.
- iPad layouts, macOS, watchOS, widgets.
- Hosted macOS CI. iOS checks run locally on the owner's Mac.
- Real devices and code signing (owner, 2026-11-11: simulator only for now). Signing settings and the
  Apple team ID arrive with Munch's App Store setup.

### Anti-goals

- **Logic in Swift.** Anything that decides, validates, formats or maps errors stays in Kotlin.
  Swift renders state, sends actions and adapts the platform.
- **An iOS app that looks like Android.** Material components imitated in SwiftUI, Android-style
  back handling, or non-native text fields would all count.
- **Touching the Android ViewModels for iOS's sake.** Swift adapts to them, not the reverse.
- **A Swift bridge out of step with the Kotlin it wraps.** That's why both live in one repo and
  change in one PR.

## 1. Approaches (decided in conversation)

1. **Where the iOS starter lives:** the foundation repo, `ios/` (owner's choice).
   - Rejected: a separate `ios-starter` repo. Kotlin and Swift would need lockstep PRs, and apps
     would need two submodules that must agree.
2. **The look:** the Gallery look, built from native parts (owner's choice).
   - Rejected: plain iOS styling for Gallery apps (loses the brand), and imitating Android
     (anti-goal).
3. **How screens use shared ViewModels:** SKIE plus one small Swift helper and typed accessors
   (owner's choice of A).
   - Rejected B, KMP-ObservableViewModel: it changes every shared ViewModel's base class,
     Android's too, and ties them to a third-party library.
   - Rejected C, KMP-NativeCoroutines: no exhaustive Swift enums, and more glue code per screen.

## 2. Layout

```
android-starter-kit/                       foundation repo
  core/shell/shared/                       NEW :core:shell:shared (KMP): the platform-neutral shell model
  core/ios/                                NEW :core:ios (KMP, iOS-facing): the entry point `Shared`, IosPlatform, accessors
  apps/showcase/shared/                    :apps:showcase:shared: the app's framework, baseName "Shared", + SKIE
  ios/
    WhitelabelKit/                         Swift package (local), reused by every app
      Package.swift
      Sources/WhitelabelKit/
        Bridge/        SharedModel, async helpers, UiText → String
        Platform/      KeychainTokenCipher, IosLanguageRepository, store paths
        DesignSystem/  StylePack (Material, Gallery), tokens, components
        Shell/         AppDefinition (Swift DSL), RootView, tabs, navigation, gate, deep links
        Features/      Settings, Home, Profile, Catalog, Authentication
      Tests/WhitelabelKitTests/            XCTest for the bridge and platform pieces
    Showcase/                              Xcode project: showcase's iOS app + UI tests
  scripts/check-ios.sh                     the iOS gate (§9)
```

**One framework name for every app** (Claude's call). WhitelabelKit's Swift code imports the
Kotlin module `Shared`, so every app's framework module uses `baseName = "Shared"`. That framework
exports `:core:ios` (the entry point and the foundation's accessors), the foundation modules
behind it, and the app's own `:shared` modules. An app's own features add their typed accessors in
the app's framework module (`iosMain`, e.g. `MunchAccessors`). Their Swift screens live in the app.
Rejected: Swift protocols for every Kotlin type, which duplicates every type; compiling the kit's
source into each app, which loses the package boundary.

**App repos:** an app gets `iosApp/`, an Xcode project next to its Android `app/`. It depends on
`foundation/ios/WhitelabelKit`, as a local package from the submodule, and on its own
`:<app>:shared` framework module.

## 3. Shared shell model (`:core:shell:shared`, KMP)

What the Android `ShellViewModel`, `TabResolution` and `DeepLinkPolicy` decide moves here
unchanged, behind a platform-neutral definition:

- **The feature model:** `FeatureSpec(id, flagKey, tabRoute: String?, requiresSignIn, isGate,
  requiresBackend)` and `DefinitionSpec(features, startTab)`.
  - Its rules are the same as the Android `AppDefinitionBuilder.build()`: duplicate ids, at least
    one tab, start tab is a tab, one gate, sign-in needs a gate, backend features need a backend.
    They become one shared `validate()`.
  - The Android `AppDefinition` builds a `DefinitionSpec` from its entries. Android keeps its
    Compose types (icons, `@StringRes`, `NavGraphBuilder`) on its side.
- **The shell ViewModel:** `ShellViewModel` (shared) takes the `DefinitionSpec`, `BrandConfig`,
  `AppConfigRepository`, `UserPreferencesRepository`, `SessionRepository`, `UserRepository` and a
  `SavedStateHandle`.
  - Its `ShellState` is exactly today's: ready, visible tab ids, theme, session, and
    `showsContent(hasGate)`. It keeps the pending deep link, `CONFIG_WAIT_MS` and once-per-process
    tabs.
  - Android's `FoundationActivity` uses it. Android behaviour must not change, checked by today's
    shell tests (moved) and showcase's E2E.
- **Deep links:** routes are strings across the boundary. Each platform maps a route id to its own
  screen.

## 4. The bridge (`:apps:showcase:shared` + `WhitelabelKit/Bridge`)

- **SKIE** is applied to the framework module: `:apps:showcase:shared`, and each app's
  `:<app>:shared`.
  - It turns `StateFlow`/`Flow` into Swift async sequences, sealed types into Swift enums, and
    `suspend` into `async`.
  - Builder's call: the SKIE version compatible with Kotlin 2.2.20.
- **The Kotlin entry point** lives in `:core:ios` (iOS-only KMP module), exported by every app's
  framework:
  ```kotlin
  object Shared {
      fun start(platform: IosPlatform, definition: DefinitionSpec, useMockApi: Boolean)
      fun settingsViewModel(): SettingsViewModel
      fun catalogListViewModel(options: CatalogOptions): CatalogListViewModel
      // … one typed accessor per shared ViewModel, plus shellViewModel()
      fun resetForTests()   // stop and restart, like Android's FreshAppRule
  }
  class IosPlatform(
      val tokenCipher: TokenCipher,
      val language: LanguageRepository,
      val storePaths: StorePaths,
      val brand: BrandConfig,
  )
  ```
  - Swift implements `TokenCipher`, `LanguageRepository` and `StorePaths` (they are Kotlin
    interfaces). The HTTP engine, `NetworkConfig`, `Analytics` (logging) and `CrashReporter` are
    built in Kotlin from `brand`.
  - `useMockApi` binds `:core:mockapi`'s `FakeBackend` engine instead of Darwin.
  - **Release never contains the fake** (the Android rule, kept; Claude's call). The framework
    module includes `:core:mockapi` only when Gradle runs with `-Pwhitelabel.mockApi=true`, which
    the Xcode Debug configuration passes to `embedAndSignAppleFrameworkForXcode`. Release builds
    without it, and `Shared.start(useMockApi = true)` then fails loudly.
    `scripts/check-ios.sh` verifies that a Release build's binary has no `FakeBackend` symbols.
  - ViewModels needing a `SavedStateHandle` get one built from the screen's arguments: the reset
    token and the catalog item id.
  - Builder's call: the accessor names, and whether accessors are generated or hand-written.
    There are about 12, so hand-written is fine.
- **`SharedModel<State, Action>`** (Swift, `@Observable`):
  - holds the ViewModel and exposes `state` (updated from the async sequence on the main actor);
  - forwards `send(_ action:)` to `onAction`, and exposes `events` as an `AsyncSequence` that
    screens consume in `.task`;
  - clears the ViewModel's store when the screen goes away.
- **`UiText` → `String`:** a SwiftUI helper resolves `UiText` with `asStringAsync()` (shared
  strings), in the device language.

## 5. Platform pieces (`WhitelabelKit/Platform`)

- **`KeychainTokenCipher: TokenCipher`.** AES-GCM via CryptoKit, with a 256-bit key kept in the
  Keychain.
  - The key is `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` and isn't synced. So a restored
    backup can't decrypt the token file, and that reads as signed out (the same rule as Android:
    `TokenStore` clears what it can't open).
  - Errors are thrown as Kotlin-visible exceptions (via SKIE's error bridging).
- **`IosLanguageRepository: LanguageRepository`.**
  - `current()` returns the app's chosen language (stored per app) or `nil`.
  - `set(_:)` stores it and writes `AppleLanguages` for the app. iOS applies it at the next
    launch. The settings row says so (a shared string).
  - Builder's call: whether to offer the system's per-app language settings link as well.
- **Store paths:** `iosJsonStorePath(name)` (exists): Application Support, `datastore/<name>.json`.

## 6. Design system (`WhitelabelKit/DesignSystem`)

- **`StylePack`:** `.material` and `.gallery`, chosen in the app's Swift definition.
  - Each pack defines colors (light/dark, from the app's asset catalog names), typography (system
    font for `.material`; for `.gallery`, the app's bundled Instrument Serif and Geist), corner
    radii, surfaces, spacing, motion (spring/duration) and haptics.
  - The values are copied by hand from the Kotlin packs. Each carries a comment naming its Kotlin
    source.
- **What stays native:** `NavigationStack` and swipe-back, `TabView`, sheets, `TextField`
  editing, `Toggle`, `Picker`, pull-to-refresh, Dynamic Type, VoiceOver labels.
- **Gallery's own styling:** cards, button styles (`ButtonStyle`), list rows, empty, loading and
  error states, headers, and the motion on press and appear.
- **Material pack:** the same components with standard iOS styling, tinted with the brand colors.
- **Reference:** Gallery matches Munch's `docs/design/design.html` (shapes, type scale, spacing)
  where an iOS equivalent exists.
- **Previews:** every component has SwiftUI previews per state, in both packs, light and dark.
- **Where Gallery is proven before Munch** (Claude's call): showcase on iOS stays Material, as on
  Android. WhitelabelKit gets a design-system catalog screen like Android's
  `DesignSystemCatalog`: every component in both packs, light and dark. It opens from showcase in
  Debug builds and uses Munch-like sample assets (the OFL Instrument Serif and Geist fonts and the
  Gallery colors) bundled in the kit's preview resources. A snapshot of it, side by side with
  `design.html`, is the check before Munch adopts it.
- **Source of truth:**
  - The Kotlin pack for what it defines: radii, sizing, motion, haptics.
  - `docs/design/tokens.json` and `design.html` for what the Kotlin pack doesn't define, since
    colors and type come from an app's brand on both platforms.
  - When they disagree, the Kotlin pack wins and the mismatch is reported to the owner.

## 7. Branding and app identity

- **`app.properties` stays the single source** for name, bundle id (= `app.applicationId`), API
  URLs, tenant, deep-link scheme and defaults.
- **A Gradle task, `generateIosConfig`, on the app's framework module,** writes
  `iosApp/Config/App.generated.xcconfig`:
  - Debug and Release values;
  - the debug API URL's Android-only host `10.0.2.2` rewritten to `localhost` for the simulator;
  - the URL scheme for deep links.

  The Xcode project reads the xcconfig. `Info.plist` takes values from it, and Swift reads them
  into `BrandConfig`.
- **Assets:** the app's colors, fonts and icons live in its `Assets.xcassets` with fixed names that
  the design system reads (mirroring Android's `brand_*` resources).

## 8. Shell and features (`WhitelabelKit/Shell`, `Features`)

- **The Swift definition:**
  ```swift
  AppDefinition(style: .gallery) { Auth(); Home(shortcuts: …); Catalog(categories: …); Profile(); Settings() }
  ```
  It builds the shared `DefinitionSpec`, which validates it, and maps each route id to a SwiftUI
  screen. An app's own features plug in the same way.
- **`RootView`:** starts `Shared.start(...)` once, shows a splash until
  `ShellState.showsContent`, shows the gate or the tabs, and applies the theme.
  - Deep links come in through `onOpenURL`, and the shared model holds them until sign-in.
- **Feature screens** use their shared ViewModels through `SharedModel`. They show every state the
  Android screen shows (loading, empty, error, offline, content) and use only shared strings for
  messages. Screen-only labels go in the package's `Localizable.xcstrings` (en, fr).
- **Order:**
  1. shell, design system, settings: the bridge's first proof;
  2. home and profile;
  3. catalog (with images: `AsyncImage`, or a small cache);
  4. authentication (gate, register, forgot, reset via deep link).

## 9. Testing and checks

- **Kotlin:** the shared shell model gets the Android shell's tests, moved to `commonTest`, so they
  run on the JVM and iOS. The iOS entry point gets an `iosTest` that starts it with test platform
  pieces and resolves every accessor.
- **Swift unit tests** (`WhitelabelKitTests`, XCTest):
  - `SharedModel` updates state, forwards actions and clears the ViewModel;
  - `KeychainTokenCipher` round-trips, and a removed key reads as signed out;
  - `IosLanguageRepository` behaves as described in §5;
  - the Swift `AppDefinition` maps to `DefinitionSpec`.
- **E2E** (showcase's UI tests, XCUITest with a robot per screen): the same main flows as Android's
  `ShowcaseE2ETest`, against `FakeBackend`, including token refresh, a revoked session, catalog off
  and the reset link (Claude's call). XCUITest runs in another process, so it controls the fake
  through:
  - **launch arguments, read before start:**
    - `-mockApi`;
    - `-fakeFeatures catalog=false`;
    - `-fakeResetToken <fixed>`, which makes the fake issue a known reset token;
    - `-freshInstall`, which runs `Shared.resetForTests()` and clears the Keychain item and store
      directory;
  - **debug-only control URLs** for mid-test actions, opened by the test through the app's scheme:
    `<scheme>://_fake/expire-tokens` and `<scheme>://_fake/revoke-sessions`;
  - **checks on what the user sees.** Request-log assertions (Android's `backend.requests`) stay on
    Android.

  The URL handler and launch-argument parsing exist only in Debug builds.
- **`scripts/check-ios.sh`** (local, macOS with Xcode):
  - Gradle's iOS tests for the shared modules;
  - `xcodebuild test` for `WhitelabelKit` and showcase's UI tests on a simulator;
  - a build of the showcase app.

  The pre-push hook runs it on macOS when `ios/` or a KMP module changed. GitHub CI stays
  Android-only.

## 10. Constraints

- Everything in the parent design's constraints still holds:
  - the foundation is changed through its own PRs;
  - the owner merges;
  - no analytics of personal data;
  - no app identity in shared code;
  - strings in English and French.
- iOS 17 minimum (`@Observable`, Swift 5.9+), and Xcode 26 (installed).
- **Android behaviour doesn't change.** Moving the shell model is checked by today's shell tests
  and showcase's E2E on Android.
- Swift code follows Swift conventions (value types for views, `@MainActor` UI, structured
  concurrency). There are no force-unwraps on data from Kotlin.

## 11. Left to the builder

- The SKIE version.
- The accessor list's exact names, as long as it's typed and covers every shared ViewModel.
- Image loading for catalog.
- The exact Swift component set beyond what the five features need.
- The xcconfig generator's details.
- Whether the pre-push hook detects `ios/` changes or always runs `check-ios.sh` on macOS.
- The structure of `WhitelabelKit` (one target or a few), keeping the folders of §2.
