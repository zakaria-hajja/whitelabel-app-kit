# iOS Starter (step 5) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Showcase runs on the iOS simulator on a reusable Swift kit (`ios/WhitelabelKit`), with
the foundation's five features at parity and XCUITests against the fake backend. The shell's
logic is shared Kotlin, and Android behaves exactly as before.

**Architecture:**
- **Seven parts, one branch and one PR each.**
  - A moves the shell's logic into `:core:shell:shared`. This is Kotlin only, and Android keeps
    working.
  - B adds the iOS entry point (`:core:ios`) and the `Shared` framework with SKIE. It also adds the
    xcconfig generator, the Swift package skeleton with the bridge and platform pieces, the
    showcase Xcode project (XcodeGen), and `check-ios.sh`.
  - C builds the design system.
  - D–G build the shell and the features: settings, authentication, home and profile, catalog.
    Each adds its screens, its showcase definition entries and its E2E flows.
- **One framework name.** WhitelabelKit imports the Kotlin module `Shared`, and every app's
  framework module is named `Shared`.

**Tech Stack:**
- Existing: Kotlin 2.2.20, Koin 4.1.1, Compose Multiplatform resources 1.9.1, Ktor Darwin.
- New:
  - SKIE: the newest release that supports Kotlin 2.2.20, picked in Task B1;
  - Swift 5.9+ / SwiftUI, iOS 17 minimum, Xcode 26;
  - XcodeGen (Homebrew), which generates `.xcodeproj` from `project.yml`;
  - XCTest and XCUITest.

**Spec:** `docs/superpowers/specs/2026-11-11-ios-starter-design.md`. Its parent design is
`docs/superpowers/specs/2026-11-11-ios-kmp-stack-design.md`.

**Out of scope** (spec non-goals):
- Munch on iOS;
- reminders and notifications, Firebase, ads and iCloud backup;
- the diagnostics screen, whose row is hidden on iOS;
- iPad, devices and code signing (simulator only);
- hosted macOS CI.

## Decisions this plan makes (owner: please confirm in review)

1. **Feature order is settings → authentication → home and profile → catalog** (the spec has
   authentication last). Home and profile set `requiresSignIn`, and the shared validation (the
   same rule as Android's `AppDefinitionBuilder.build()`) rejects sign-in features without a gate.
   So showcase can't include them on iOS before `Auth()` exists. Munch only needs shell and
   settings, so it waits no longer.
2. **XcodeGen generates the Xcode projects.** The checked-in `project.yml` is the source, and the
   generated `.xcodeproj` is git-ignored. `check-ios.sh` and the docs run `xcodegen` first.
   Hand-editing `project.pbxproj` in PRs would be unreviewable.
3. **How WhitelabelKit reaches the `Shared` framework is proven first, in Task B3**, with a
   minimal end-to-end slice before anything is built on it.
   - The spec's plan: a local Swift package whose targets see the app's Gradle-built `Shared`
     framework through `FRAMEWORK_SEARCH_PATHS` from the app's build settings.
   - If a local package can't see a framework produced by the app's own build phase, the fallback
     order is:
     1. WhitelabelKit as an Xcode framework target defined in `ios/WhitelabelKit/project.yml`,
        which apps include as a subproject;
     2. WhitelabelKit's sources compiled into the app target through an XcodeGen source group.
   - The executor rules on this, ledgers it, and says so in the PR. Everything after Task B3
     depends only on `import WhitelabelKit` and `import Shared` working, whichever way it's done.

## Global Constraints

- **Logic stays in Kotlin.** Swift renders state, sends actions and adapts the platform. No
  validation, formatting, error mapping or flag/tab/deep-link decisions in Swift (spec anti-goal).
- **Android behaviour does not change.** Every Android check of the migration plan's part
  checkpoint stays green, including showcase's E2E (16 tests).
- **iOS 17 minimum, Xcode 26, simulator only, no code signing.**
- **Release never contains `FakeBackend`.** `:core:mockapi` is linked only with
  `-Pwhitelabel.mockApi=true`, which only the Xcode Debug configuration passes.
- **Messages from ViewModels use shared strings** (`UiText` → `asStringAsync()`). Screen-only
  labels live in `ios/WhitelabelKit/.../Localizable.xcstrings`, in English and French.
- **Swift style:**
  - views are value types and UI code is `@MainActor`;
  - structured concurrency (`.task`, no detached tasks);
  - no force-unwraps on values from Kotlin;
  - every component has `#Preview`s per state;
  - accessibility labels on icon-only buttons.
- **No app identity in WhitelabelKit.** Name, bundle id, URLs and scheme come from the app's
  generated xcconfig, and colors and fonts from the app's asset catalog.
- **Git:** per `CLAUDE.md`, one branch and one PR per part:

  | Part | Branch |
  |---|---|
  | A | `feat/ios-shell-model` |
  | B | `feat/ios-bridge` |
  | C | `feat/ios-design-system` |
  | D | `feat/ios-shell-settings` |
  | E | `feat/ios-auth` |
  | F | `feat/ios-home-profile` |
  | G | `feat/ios-catalog` |
- **Part checkpoint:** the migration plan's Android checkpoint, plus `scripts/check-ios.sh` once
  it exists (from B). Then the docs the part names, the PR, CI green, and a report. The owner
  merges.

## Review Focus

1. **A cold start with a slow or failing config fetch on iOS.** The splash must end within
   `CONFIG_WAIT_MS` (2 s) and show the build-time default tabs, never hang. Pinned in Task D1 with
   a UI test: launch with `-fakeConfigDelayMs 5000` and assert the first tab appears within 4 s.
2. **A reset link arriving while signed in, and a catalog link while signed out.** The shared
   `deepLinkAction` must decide, and Swift must obey: ignore the first, hold the second until
   sign-in. Pinned in Task E3 (reset while signed in is ignored) and Task G2 (catalog link held
   until sign-in).
3. **Tokens surviving an app reinstall or a restored backup.** The Keychain key is
   `ThisDeviceOnly`, so a token file it can't decrypt must read as signed out, never crash.
   Pinned in Task B3 (`KeychainTokenCipherTests.missingKeyReadsAsSignedOut`) and Task E3 (UI
   test: delete the Keychain item between launches → sign-in screen).
4. **Dynamic Type at the largest sizes.** Gallery cards and buttons must wrap, not truncate or
   overlap. Pinned in Task C2: catalog previews at `.accessibility3` and a snapshot-free UI test
   that finds the full button label.
5. **Language set in-app on iOS.** It applies at the next launch, and the settings row says so.
   Never a half-translated screen. Pinned in Task D2: a UI test selects French and asserts the
   "applies after restart" note (shared string), and the screen stays in the launch language.

---

## Part A: the shared shell model (`feat/ios-shell-model`)

### Task A1: `:core:shell:shared`: definition spec, validation, tabs and deep-link policy

**Files:**
- Create: `core/shell/shared/build.gradle.kts` (`whitelabel.kmp.library`, `whitelabel.koin`;
  depends on `:core:domain`, `:core:branding`, `:core:common`)
- Create: `core/shell/shared/src/commonMain/kotlin/com/whitelabel/core/shell/model/DefinitionSpec.kt`
- Move: `TabResolution.kt` and `DeepLinkPolicy.kt` logic into
  `core/shell/shared/src/commonMain/kotlin/com/whitelabel/core/shell/model/`, operating on the spec
- Modify: `core/shell/src/main/kotlin/com/whitelabel/core/shell/AppDefinition.kt`:
  - `build()` delegates its rules to `DefinitionSpec.validate()`;
  - add `internal fun AppDefinition.toSpec(): DefinitionSpec`.
- Modify: `gradle/foundation.settings.gradle.kts` (`:core:shell:shared`); `core/shell/build.gradle.kts`
  (`api(project(":core:shell:shared"))`)
- Test: move `TabResolutionTest`, `DeepLinkPolicyTest` and `AppDefinitionTest`'s rule tests to
  `core/shell/shared/src/commonTest/...`. The Android-side `AppDefinitionTest` keeps the tests of
  Compose-typed parts (tabs' order, style).

**Interfaces:**
- Produces:
  - `data class FeatureSpec(val id: String, val flagKey: String?, val tabRoute: String?, val requiresSignIn: Boolean, val isGate: Boolean, val requiresBackend: Boolean)`
  - `data class DefinitionSpec(val features: List<FeatureSpec>, val startTabRoute: String?)`, with:
    - `fun validate(): DefinitionSpec`, which throws `IllegalArgumentException` with today's
      messages;
    - `fun backendProblem(hasBackend: Boolean): String?`;
    - `val hasGate: Boolean`.
  - `fun DefinitionSpec.visibleTabRoutes(remoteFlags: Map<String, Boolean>, defaultFeatures: Set<String>): List<String>`
  - `fun DefinitionSpec.resolveStartTab(visible: List<String>): String?`
  - `enum class DeepLinkAction { OPEN, HOLD, IGNORE }` and
    `fun deepLinkAction(inMain: Boolean, inAuth: Boolean, signedIn: Boolean, hasGate: Boolean): DeepLinkAction`
    (public now).
  - Route ids are explicit strings shared by both platforms.
    - Each feature's `:shared` module declares `object <Name>Routes`, e.g.
      `SettingsRoutes.ROOT = "settings"`, `CatalogRoutes.LIST = "catalog"`,
      `CatalogRoutes.DETAIL = "catalog/detail"`, `AuthRoutes.LOGIN = "auth/login"`,
      `AuthRoutes.RESET = "auth/reset"`, `HomeRoutes.ROOT = "home"`, `ProfileRoutes.ROOT = "profile"`.
    - Android's `Tab` gets `val routeId: String`, which each feature's DSL sets from those
      constants, and `toSpec()` uses it.
    - Android navigation keeps its route objects.
- [ ] **Step 1:** Move the tests as listed and rewrite their fixtures as `FeatureSpec`s. Run
  `./gradlew :core:shell:shared:allTests`. Expected: FAIL (module missing).
- [ ] **Step 2:** Implement the spec types and functions; `AppDefinition.build()` calls
  `toSpec().validate()`.
- [ ] **Step 3:** Run `./gradlew :core:shell:shared:allTests :core:shell:testDebugUnitTest assembleDebug`.
  Expected: PASS on JVM and iOS. The Android shell tests are unchanged and green.
- [ ] **Step 4:** Commit `feat(shell): share the app definition rules, tabs and deep-link policy`.

### Task A2: the shared `ShellViewModel`

**Files:**
- Move: `ShellViewModel.kt` (and `ShellState`, `CONFIG_WAIT_MS`) to
  `core/shell/shared/src/commonMain/kotlin/com/whitelabel/core/shell/model/`. Its constructor
  takes `DefinitionSpec` instead of `AppDefinition`.
- Modify:
  - `ShellState.visibleTabs` becomes `visibleTabRoutes: List<String>`;
  - `FoundationActivity`/`ShellNavHost` map routes back to their `Tab`s through the definition;
  - `shellModule` passes `definition.toSpec()`.
- Create: `core/shell/shared/src/commonMain/.../model/ShellKoin.kt`:
  `val shellModelModule = module { viewModel { ShellViewModel(get(), get(), get(), get(), get(), get(), get()) } }`.
  The Android `shellModule` stops declaring it and includes `shellModelModule`.
- Test: move `ShellViewModelTest` and `ShellStateTest` to `commonTest`, rewritten with
  `DefinitionSpec`.

**Interfaces:**
- Produces: `class ShellViewModel(definition: DefinitionSpec, brand: BrandConfig, appConfig: AppConfigRepository, preferences: UserPreferencesRepository, session: SessionRepository, users: UserRepository, savedState: SavedStateHandle) : ViewModel`.
  - It has `state: StateFlow<ShellState>`, `pendingDeepLink: StateFlow<String?>`,
    `onDeepLink(link: String)` and `onDeepLinkConsumed()`.
  - `ShellState(ready, visibleTabRoutes, theme, session)` with `showsContent(hasGate)`.
- [ ] **Step 1:** Move the tests and adapt them to routes. Run `./gradlew :core:shell:shared:allTests`.
  Expected: FAIL (types missing).
- [ ] **Step 2:** Move and adapt the ViewModel, and the Android mapping.
- [ ] **Step 3:** Run:
  - `./gradlew :core:shell:shared:allTests testDebugUnitTest assembleDebug`;
  - `./gradlew :apps:showcase:connectedDebugAndroidTest` on an idle emulator.

  Expected: all green, with showcase 16/16.
- [ ] **Step 4:** Commit `feat(shell): share the shell view model`.

### Task A3: Part A checkpoint and docs

- [ ] Update `docs/architecture.md`:
  - a `:core:shell:shared` row;
  - the shell section: the shared model, and Android mapping routes to `Tab`s.
- [ ] Run the part checkpoint, commit `docs(shell): document the shared shell model`, push, and
  open the PR.

---

## Part B: the bridge (`feat/ios-bridge`)

### Task B1: `:core:ios`, the `Shared` framework and SKIE

**Files:**
- Modify: `gradle/libs.versions.toml` (SKIE plugin `co.touchlab.skie`; verify a release supports
  Kotlin 2.2.20, and record the version in the PR)
- Create: `core/ios/build.gradle.kts` (`whitelabel.kmp.library`, `whitelabel.koin`; iOS source sets only)
- Create: `core/ios/src/iosMain/kotlin/com/whitelabel/core/ios/Shared.kt`, `IosPlatform.kt`, `MockApi.kt`
- Modify: `apps/showcase/shared/build.gradle.kts`:
  - `baseName = "Shared"`;
  - apply SKIE;
  - export `:core:ios` and `:core:shell:shared`;
  - `:core:mockapi` as `api` only when `providers.gradleProperty("whitelabel.mockApi") == "true"`.
- Modify: `gradle/foundation.settings.gradle.kts` (`:core:ios`)
- Test: `core/ios/src/iosTest/kotlin/com/whitelabel/core/ios/SharedEntryIosTest.kt`;
  `apps/showcase/shared/src/iosTest/...` (`ShowcaseGraphIosTest` switches to `Shared.start`)

**Interfaces:**
- Produces, in `com.whitelabel.core.ios`:
  - `class IosPlatform(val tokenCipher: TokenCipher, val language: LanguageRepository, val storePaths: StorePaths, val brand: BrandConfig, val logger: (String) -> Unit)`
  - `object Shared`:
    - `fun start(platform: IosPlatform, definition: DefinitionSpec, appModules: List<Module>, useMockApi: Boolean)`.
      It builds the network config from `brand` (user agent `"<applicationId>/<versionName> (iOS <version>)"`)
      and `defaultHttpEngine()`, or the fake engine when `useMockApi`. `Analytics` and
      `CrashReporter` log through `platform.logger`.
    - `fun resetForTests()`: stop, join the application scope, and start again with the same
      arguments.
    - Accessors, one per shared ViewModel:
      - `shellViewModel()`
      - `settingsViewModel()`
      - `homeViewModel()`
      - `profileViewModel()`
      - `changePasswordViewModel()`
      - `loginViewModel(email: String?)`
      - `registerViewModel()`
      - `forgotPasswordViewModel(email: String?)`
      - `resetPasswordViewModel(token: String)`
      - `catalogListViewModel(options: CatalogOptions)`
      - `catalogDetailViewModel(id: String)`

      Each builds a `SavedStateHandle` from its arguments, with the same keys as Android's routes:
      `"token"`, `"id"`, `"email"`.
    - `fun fakeBackend(): FakeBackend?`: non-null only with `useMockApi` (debug).
  - The features' route constants (Task A1) are reachable from Swift through the export.
- [ ] **Step 1: Write the failing test** `SharedEntryIosTest`:
  - `start` with test platform pieces;
  - every accessor returns its ViewModel type;
  - `resetForTests()` gives a new `shellViewModel()` whose state starts with `ready == false`;
  - `start(useMockApi = true)` without `:core:mockapi` linked throws an error naming the Gradle
    property.
- [ ] **Step 2:** Run `./gradlew :core:ios:iosSimulatorArm64Test`. Expected: FAIL.
- [ ] **Step 3:** Implement. `Shared.start` calls `startKoin` with:
  - `networkModule`, `dataModule` and `shellModelModule`;
  - the app's modules;
  - a platform module built from `IosPlatform`;
  - the mock module when asked.
- [ ] **Step 4:** Run:
  - `./gradlew :core:ios:iosSimulatorArm64Test :apps:showcase:shared:iosSimulatorArm64Test`;
  - `-Pwhitelabel.mockApi=true :apps:showcase:shared:linkDebugFrameworkIosSimulatorArm64`;
  - `:apps:showcase:shared:linkReleaseFrameworkIosSimulatorArm64` (without the property).

  Then `nm` the Release binary and grep for `FakeBackend`. Expected: tests PASS, both links PASS,
  and no `FakeBackend` symbols in Release.
- [ ] **Step 5:** Commit `feat(ios): add the shared ios entry point and framework`.

### Task B2: `generateIosConfig` (app identity → xcconfig)

**Files:**
- Create: `build-logic/convention/src/main/kotlin/com/whitelabel/buildlogic/apps/IosConfig.kt`
  (a pure function)
- Create: a task class plus registration in a new `whitelabel.ios.app` plugin, applied by
  `:apps:showcase:shared`. It reads the Android app's `app.properties` (path from the plugin's
  extension `appProperties`).
- Test: `build-logic/convention/src/test/kotlin/com/whitelabel/buildlogic/apps/IosConfigTest.kt`

**Interfaces:**
- Produces: `internal fun iosXcconfig(app: AppConfig, buildType: String): String` (reusing
  `AppConfigLoader`'s `AppConfig`). It writes these keys:
  - `WL_APP_NAME`, `WL_BUNDLE_ID` (`applicationId`, plus `.debug` for Debug, as on Android);
  - `WL_VERSION_NAME`, `WL_VERSION_CODE`;
  - `WL_API_BASE_URL` (debug or release value; host `10.0.2.2` → `localhost`);
  - `WL_TENANT`, `WL_DEEP_LINK_SCHEME`, `WL_ENVIRONMENT` (`DEVELOPMENT` for Debug, `PRODUCTION`
    for Release);
  - `WL_SUPPORT_EMAIL`, `WL_PRIVACY_URL`, `WL_TERMS_URL`, `WL_DEFAULT_FEATURES` (comma-separated).

  `//` in URLs is escaped as `/$()/`, as xcconfig needs. The task `generateIosConfig` writes
  `Debug.generated.xcconfig` and `Release.generated.xcconfig` into the app's iOS `Config/` dir.
- [ ] **Step 1: Write the failing tests** `IosConfigTest`:
  - `debug uses the debug url with localhost`:
    `http://10.0.2.2:3000/api/v1/` → `WL_API_BASE_URL = http:/$()/localhost:3000/api/v1/`;
  - `debug bundle id gets the debug suffix`;
  - `release uses the release url and PRODUCTION`;
  - `a backendless app has an empty url`;
  - `values with = or spaces are written verbatim after the key`.
- [ ] **Step 2:** Run `./gradlew -p build-logic test --tests '*IosConfigTest*'`. Expected: FAIL.
- [ ] **Step 3:** Implement the function, task and plugin.
- [ ] **Step 4:** Run the tests, then `./gradlew :apps:showcase:shared:generateIosConfig`.
  Expected: PASS, and the two files are written under `ios/Showcase/Config/`.
- [ ] **Step 5:** Commit `build(ios): generate the app's ios config from app.properties`.

### Task B3: WhitelabelKit skeleton, the bridge, the platform pieces, and showcase's Xcode project

**Files:**
- Create: `ios/WhitelabelKit/Package.swift` (iOS 17; one library target `WhitelabelKit`; a test
  target `WhitelabelKitTests`)
- Create: `ios/WhitelabelKit/Sources/WhitelabelKit/Bridge/SharedModel.swift`, `UiTextView.swift`
- Create: `ios/WhitelabelKit/Sources/WhitelabelKit/Platform/KeychainTokenCipher.swift`,
  `IosLanguageRepository.swift`, `PlatformFactory.swift`
- Create: `ios/Showcase/project.yml` (XcodeGen):
  - an app target `Showcase` with a pre-build script phase:
    `cd "$SRCROOT/../.." && ./gradlew :apps:showcase:shared:embedAndSignAppleFrameworkForXcode`
    plus `-Pwhitelabel.mockApi=true` in Debug;
  - framework search paths to Gradle's output;
  - configs from `Config/*.generated.xcconfig`;
  - the WhitelabelKit dependency;
  - a UI test target `ShowcaseUITests`.
- Create: `ios/Showcase/Showcase/ShowcaseApp.swift` (temporary: shows the resolved English
  greeting from a shared string, to prove the slice), `Info.plist` keys from the xcconfig
- Create: `scripts/check-ios.sh`, run in order:
  1. `generateIosConfig`;
  2. `xcodegen` for `ios/Showcase`;
  3. Gradle iOS tests for the KMP modules;
  4. `xcodebuild test` for WhitelabelKit and for Showcase (UI tests) on `iPhone 18 Pro`;
  5. a Release build plus the `FakeBackend` symbol check.

  It skips with a clear message if `xcodegen` is missing (`brew install xcodegen`).
- Modify: `.gitignore` (`ios/**/*.xcodeproj`, `ios/**/*.generated.xcconfig`, `ios/**/build/`)
- Test: `ios/WhitelabelKit/Tests/WhitelabelKitTests/SharedModelTests.swift`,
  `KeychainTokenCipherTests.swift`, `IosLanguageRepositoryTests.swift`

**Interfaces:**
- Produces, in Swift (`WhitelabelKit`):
  - `@MainActor @Observable final class SharedModel<State: AnyObject, Action: AnyObject>`:
    - `init(_ viewModel: Lifecycle_viewmodelViewModel, state: SkieSwiftStateFlow<State>, onAction: @escaping (Action) -> Void)`
    - `var state: State`, `func send(_ action: Action)`
    - `deinit` clears the ViewModel's store.

    The exact SKIE type names are taken from the generated header; the executor may wrap them, and
    records any rename.
  - `struct UiTextView: View`: renders a shared `UiText` via `asStringAsync()`, keeping the last
    value while a new one resolves. Also `func resolve(_ text: UiText) async -> String`.
  - `final class KeychainTokenCipher: TokenCipher`:
    - `init(service: String)` (bundle id);
    - AES-GCM (CryptoKit) with a 256-bit key in the Keychain (`kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`, not synchronizable);
    - the IV is a 12-byte nonce.
  - `final class IosLanguageRepository: LanguageRepository`:
    - `init(defaults: UserDefaults)`;
    - `current()` returns the stored choice;
    - `set(_:)` stores it and writes `AppleLanguages`.
  - `enum PlatformFactory { static func make(bundle: Bundle) -> IosPlatform }`: reads the
    `WL_*` keys from `Info.plist` into `BrandConfig`, and uses `iosJsonStorePath`.
- [ ] **Step 1: Write the failing tests** (Swift):
  - `SharedModelTests.updatesStateAndForwardsActions`: with `Shared.start(..., useMockApi: true)`,
    a `SettingsViewModel` model reflects `OnThemeSelect(.dark)` in `state.theme`.
  - `SharedModelTests.clearsTheViewModelWhenReleased`: weak-reference check.
  - `KeychainTokenCipherTests.roundTrips`.
  - `KeychainTokenCipherTests.missingKeyReadsAsSignedOut`: encrypt, delete the Keychain item, and
    a `TokenStore` through the cipher reads `nil` tokens without throwing to Swift.
  - `IosLanguageRepositoryTests.storesTheChoice` and `.nilFollowsTheDevice`.
- [ ] **Step 2:** Run `xcodebuild test -scheme WhitelabelKit -destination 'platform=iOS Simulator,name=iPhone 18 Pro'`
  from `ios/WhitelabelKit`. Expected: FAIL (types missing). If the package can't see `Shared` at
  all, apply the fallback order from "Decisions" 3, ledger the ruling, and re-run.
- [ ] **Step 3:** Implement. Then run `brew install xcodegen` (dev tool; say so in the PR) and
  `scripts/check-ios.sh`.
- [ ] **Step 4:** Expected:
  - the Swift tests PASS;
  - the Showcase app builds and launches on the simulator, showing "Hello, Alice"
    (shared string, Debug);
  - the Release check finds no `FakeBackend`.
- [ ] **Step 5:** Commit `feat(ios): add the swift bridge, platform pieces and showcase project`.

### Task B4: Part B checkpoint and docs

- [ ] Create `docs/ios.md`: setup (Xcode, `xcodegen`), `check-ios.sh`, how the framework and
  strings reach the app, the bridge, and the platform pieces. Update `docs/architecture.md`
  (`:core:ios`, `ios/`) and `docs/testing.md` (Swift tests, iOS E2E).
- [ ] Modify `.githooks/pre-push`: on macOS, run `scripts/check-ios.sh` when the pushed range
  touches `ios/`, `core/`, `feature/` or `apps/*/shared` (`git diff --name-only`). It stays
  silent elsewhere and keeps today's `check.sh`.
- [ ] Run the part checkpoint, including `check-ios.sh`, then commit, push and open the PR.

---

## Part C: the design system (`feat/ios-design-system`)

### Task C1: style packs and tokens

**Files:**
- Create: `ios/WhitelabelKit/Sources/WhitelabelKit/DesignSystem/StylePack.swift`, `Tokens.swift`,
  `BrandColors.swift`, `BrandFonts.swift`, `Environment+Style.swift`
- Test: `ios/WhitelabelKit/Tests/WhitelabelKitTests/StylePackTests.swift`

**Interfaces:**
- Produces:
  - `struct StylePack`:
    - `static let material`, `static let gallery`;
    - its fields: `radii(small, medium, large)`, `spacing(xs…xl)`, `motion(spring response/damping, reduced)`,
      `haptics(enabled)`, `type(display, title, body, label)`, `surfaces(cardShadow, cardBorder)`.

    Every Gallery value carries a comment `// Kotlin: StylePack.Gallery.<field>`, copied from
    `core/designsystem/.../StylePack` (Kotlin wins over `design.html`; report mismatches in the
    PR).
  - `BrandColors` reads asset-catalog colors by fixed names, mirroring Android's `brand_*`:
    `BrandPrimary`, `BrandOnPrimary`, `BrandSurface`, `BrandOnSurface`, `BrandBackground`,
    `BrandError`, … (the list taken from `docs/branding.md`), each with light and dark variants.
  - `BrandFonts`: `.material` uses system fonts. `.gallery` uses the app's bundled `brand_display`
    and `brand_body` font files, falling back to system fonts when absent.
  - SwiftUI environment values `\.stylePack` and `\.brandColors`.
- [ ] **Step 1:** Tests:
  - `StylePackTests.galleryMatchesKotlin`: every Gallery value equals the Kotlin value written in
    the test, for radii, spacing and motion.
  - `reducedMotionUsesThePacksReducedValues`.
  - `missingBrandFontFallsBackToSystem`.
- [ ] **Step 2:** Run `xcodebuild test -scheme WhitelabelKit …`. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run the same. Expected: PASS.
- [ ] **Step 5:** Commit `feat(ios): add the style packs and brand tokens`.

### Task C2: components

**Files:**
- Create: `ios/WhitelabelKit/Sources/WhitelabelKit/DesignSystem/Components/`:
  - `AppButtonStyles.swift` (primary, secondary, text)
  - `AppCard.swift`
  - `AppTextField.swift` (a native `TextField` with label, error and secure variant)
  - `AppListRow.swift` (value row, toggle row, choice sheet)
  - `LoadingView.swift`, `EmptyStateView.swift`, `ErrorStateView.swift` (message + retry)
  - `ConfirmDialog.swift` (a native `.confirmationDialog`/`.alert` wrapper)
- Test: `ios/WhitelabelKit/Tests/WhitelabelKitTests/ComponentTests.swift`; previews in each file

**Interfaces:**
- Produces:
  - `PrimaryButtonStyle`, `SecondaryButtonStyle`, `TextButtonStyle`: `ButtonStyle`s that read
    `\.stylePack`.
  - `AppCard<Content>`, `AppTextField(label:text:error:isSecure:)` (`error: String?`),
    `AppValueRow(label:value:action:)`, `AppToggleRow(label:isOn:)`,
    `AppChoiceSheet<T>(title:choices:selected:label:onSelect:)`.
  - `LoadingView`, `EmptyStateView(title:message:)`, `ErrorStateView(message:onRetry:)`.
  - All use native controls underneath, with Gallery or Material styling from the environment.
- [ ] **Step 1:** Tests (view inspection without third-party libraries, using `ImageRenderer`
  sizes and accessibility):
  - `buttonsExposeTheirLabelToVoiceOver`;
  - `textFieldShowsErrorAsAccessibilityValue`;
  - `primaryButtonWrapsAtAccessibility3` (rendered at `.accessibility3` with a long label, height
    grows and width stays within the container): Review Focus 4.
- [ ] **Step 2:** Run. Expected: FAIL.
- [ ] **Step 3:** Implement, with `#Preview`s per state, in both packs, light and dark.
- [ ] **Step 4:** Run. Expected: PASS.
- [ ] **Step 5:** Commit `feat(ios): add the design system components`.

### Task C3: the design-system catalog screen and Part C checkpoint

**Files:**
- Create: `ios/WhitelabelKit/Sources/WhitelabelKit/DesignSystem/DesignSystemCatalog.swift`
  (every component in both packs, a light/dark toggle)
- Create: `ios/WhitelabelKit/Sources/WhitelabelKit/Resources/Preview/`: Instrument Serif and Geist
  (OFL, with their license files), and Gallery sample colors from Munch's `docs/design/tokens.json`
  as an asset catalog used only by the catalog and previews.
- Modify: `ios/Showcase/Showcase/ShowcaseApp.swift` (Debug: a launch argument `-designCatalog`
  opens the catalog)
- Modify: `docs/ios.md` (design system section; how to compare with `design.html`)

- [ ] **Step 1:** UI test `DesignCatalogUITests.opensWithBothPacks`: launch with `-designCatalog`,
  and assert the "Gallery" and "Material" sections and a primary button exist. Run it. Expected:
  FAIL.
- [ ] **Step 2:** Implement, then run it. Expected: PASS.
- [ ] **Step 3:** Take simulator screenshots of the catalog (both packs, light and dark) and attach
  them to the PR next to `design.html`, noting any mismatch for the owner.
- [ ] **Step 4:** Run the part checkpoint, then commit `feat(ios): add the design system catalog`,
  push and open the PR.

---

## Part D: shell and settings (`feat/ios-shell-settings`)

### Task D1: the Swift app definition and `RootView`

**Files:**
- Create: `ios/WhitelabelKit/Sources/WhitelabelKit/Shell/AppDefinition.swift`, `Feature.swift`,
  `RootView.swift`, `TabsView.swift`, `DeepLinks.swift`, `DebugControls.swift` (`#if DEBUG`)
- Modify: `ios/Showcase/Showcase/ShowcaseApp.swift`: `AppDefinition(style: .material) { Settings() }`
  (more features join in later parts)
- Test: `ios/WhitelabelKit/Tests/WhitelabelKitTests/AppDefinitionTests.swift`;
  `ios/Showcase/ShowcaseUITests/ShellUITests.swift`

**Interfaces:**
- Produces:
  - `protocol Feature { var spec: FeatureSpec { get }; func tab() -> TabItem?; func routes(_ route: String, _ args: [String: String]) -> AnyView? }`
  - `struct TabItem { let route: String; let title: LocalizedStringKey; let systemImage: String }`
  - `@resultBuilder enum FeatureBuilder`
  - `struct AppDefinition { init(style: StylePack, startTab: String? = nil, modules: [Koin_coreModule] = [], @FeatureBuilder _ features: () -> [Feature]) }`,
    with `var spec: DefinitionSpec` (validated in Kotlin; a validation failure is a
    `fatalError` naming the Kotlin message at app start, as Android crashes at start).
  - `struct RootView: View { init(definition: AppDefinition) }`. It:
    - calls `Shared.start(...)` once, with `PlatformFactory.make(bundle: .main)`;
    - reads launch arguments in Debug (`-mockApi`, `-freshInstall`, `-fakeFeatures`,
      `-fakeResetToken`, `-fakeConfigDelayMs`);
    - shows a splash until `state.showsContent(hasGate)`, then the gate's root or `TabsView`
      (a native `TabView` of `NavigationStack`s, one per visible tab route, in order);
    - applies `state.theme` (`.preferredColorScheme`);
    - handles `onOpenURL` through `deepLinkAction`, and in Debug the `_fake/…` control links.
- [ ] **Step 1: Write the failing tests:**
  - `AppDefinitionTests.mapsFeaturesToTheSpecInOrder`.
  - `AppDefinitionTests.invalidDefinitionReportsTheKotlinMessage`: two gates → the validation
    error text.
  - `ShellUITests.showsTheSettingsTabAfterTheSplash`.
  - `ShellUITests.slowConfigStillShowsTabsWithin4Seconds`: `-fakeConfigDelayMs 5000`. This is
    Review Focus 1. `FakeBackend` gets a test-only delay; add it in `:core:mockapi` with a unit
    test.
- [ ] **Step 2:** Run `scripts/check-ios.sh`. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run `scripts/check-ios.sh`. Expected: PASS.
- [ ] **Step 5:** Commit `feat(ios): add the app definition and root shell`.

### Task D2: the settings screen

**Files:**
- Create: `ios/WhitelabelKit/Sources/WhitelabelKit/Features/Settings/Settings.swift` (the `Feature`),
  `SettingsScreen.swift`, `SettingsSection.swift` (an app's own sections, mirroring Android's
  `SettingsSection`)
- Modify:
  - `feature/settings/shared/.../composeResources/values{,-fr}/strings.xml`: add
    `settings_language_applies_on_restart` ("Takes effect the next time you open the app." /
    "S'applique à la prochaine ouverture de l'app."), shown on iOS after a language change;
  - `SettingsState` gets `languageAppliesOnRestart: Boolean`, set by `IosLanguageRepository`
    through a new `LanguageRepository.appliesImmediately: Boolean` (`true` on Android).
- Create: `ios/WhitelabelKit/Sources/WhitelabelKit/Resources/Localizable.xcstrings` (screen labels:
  "Settings", "Theme", "Language", "About", "Privacy policy", "Terms", "Sign out", …; en/fr, the
  same texts as the Android `values`)
- Test: `feature/settings/shared/src/commonTest/.../SettingsViewModelTest.kt` (new cases);
  `ios/Showcase/ShowcaseUITests/SettingsUITests.swift` + `Robots/SettingsRobot.swift`

**Interfaces:**
- Consumes: `Shared.settingsViewModel()`, `SharedModel`, the design system, `UiTextView`.
- Produces: `struct Settings: Feature { init(options: SettingsOptions = .init()) }` and
  `struct SettingsOptions { var showNotifications = true; var languages = ["en", "fr"]; var sections: [SettingsSection] = []; var aboutNote: LocalizedStringKey? = nil }`.
  It shows theme, language, notifications (signed in), about, legal links (opened in Safari) and
  sign out, as Android does. The diagnostics row is hidden.
- [ ] **Step 1: Write the failing tests:**
  - Kotlin: `SettingsViewModelTest`:
    - `a language change that applies on restart says so`;
    - `a language change that applies at once says nothing`.
  - UI: `SettingsUITests.themeChangeAppliesAtOnce`.
  - UI: `SettingsUITests.languageChangeSaysItAppliesOnRestart` (Review Focus 5): pick Français,
    and the note shows. The screen title is still "Settings".
  - UI: `SettingsUITests.legalLinkOpensSafari` (asserts `XCUIApplication(bundleIdentifier: "com.apple.mobilesafari")` is running).
- [ ] **Step 2:** Run `./gradlew :feature:settings:shared:allTests` and `scripts/check-ios.sh`.
  Expected: FAIL.
- [ ] **Step 3:** Implement. Then re-run `:feature:settings:connectedDebugAndroidTest` (Android
  unchanged).
- [ ] **Step 4:** Run the same. Expected: PASS.
- [ ] **Step 5:** Commit `feat(ios): add the settings screen`.

### Task D3: Part D checkpoint and docs

- [ ] `docs/ios.md`: the app definition and features; how an app's own iOS screens plug in.
  `docs/creating-a-standalone-app.md`: a §12 on adding `iosApp/` to an app repo (`project.yml`
  modeled on showcase's, the framework module with `whitelabel.ios.app`, its `Shared` name).
- [ ] Run the part checkpoint, then commit, push and open the PR.

---

## Part E: authentication (`feat/ios-auth`)

### Task E1: sign-in, registration and the gate

**Files:**
- Create: `ios/WhitelabelKit/Sources/WhitelabelKit/Features/Authentication/Auth.swift`,
  `LoginScreen.swift`, `RegisterScreen.swift`, `AuthForm.swift`
- Modify: `ios/Showcase/Showcase/ShowcaseApp.swift` (`Auth()` first)
- Test: `ios/Showcase/ShowcaseUITests/AuthUITests.swift` + `Robots/SignInRobot.swift`

**Interfaces:**
- Produces: `struct Auth: Feature` (the gate). Its screens use `Shared.loginViewModel(email:)` and
  `Shared.registerViewModel()`, and send the actions the Android screens send.
  - The app name in the title comes from `BrandConfig.appName`.
  - Field errors are shared `UiText`.
- [ ] **Step 1: Write the failing tests:**
  - `AuthUITests.signInGreetsAndSignOutReturns`: alice / `Password123!` → the signed-in tab.
    Sign out from settings → "Sign in to Showcase".
  - `AuthUITests.wrongPasswordStaysWithTheMessage`: the shared message "The email or password is
    wrong.", in Android's exact text.
  - `AuthUITests.registerLandsSignedIn`.
- [ ] **Step 2:** Run `scripts/check-ios.sh`. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run the same. Expected: PASS.
- [ ] **Step 5:** Commit `feat(ios): add sign-in and registration`.

### Task E2: forgot and reset password, with deep links

**Files:**
- Create: `ForgotPasswordScreen.swift`, `ResetPasswordScreen.swift` (in `Features/Authentication/`)
- Modify:
  - `Shell/DeepLinks.swift`: parses `<scheme>://reset-password?token=…` into the auth route with
    args.
  - `ios/Showcase/project.yml`: the URL scheme from `WL_DEEP_LINK_SCHEME`.
- Test: `AuthUITests` (more cases)

**Interfaces:**
- Consumes: `Shared.forgotPasswordViewModel(email:)`, `Shared.resetPasswordViewModel(token:)`,
  `deepLinkAction`.
- [ ] **Step 1: Write the failing tests:**
  - `AuthUITests.resetLinkResetsThePassword`: launch with `-fakeResetToken t1`, open
    `showcase://reset-password?token=t1`, set a new password, and expect "Your password has been
    changed…".
  - `AuthUITests.forgotSendsTheLink`.
- [ ] **Step 2:** Run. Expected: FAIL.
- [ ] **Step 3:** Implement. The fake's `-fakeResetToken` support goes in `:core:mockapi`
  (`FakeBackend.fixedResetToken`, with a unit test).
- [ ] **Step 4:** Run. Expected: PASS.
- [ ] **Step 5:** Commit `feat(ios): add password reset and its deep link`.

### Task E3: session edge cases and Part E checkpoint

- [ ] **Step 1: Write the failing tests:**
  - `AuthUITests.expiredTokenRefreshesSilently`: sign in, open `showcase://_fake/expire-tokens`,
    pull to refresh on a signed-in tab, and still signed in.
  - `AuthUITests.revokedSessionReturnsToSignIn`.
  - `AuthUITests.resetLinkWhileSignedInIsIgnored` (Review Focus 2).
  - `AuthUITests.unreadableTokenFileStartsSignedOut` (Review Focus 3): sign in, terminate, delete
    the Keychain key with a Debug launch argument `-dropTokenKey`, relaunch, and expect the sign-in
    screen.
  - `AuthUITests.relaunchWithASessionStaysSignedIn`.
- [ ] **Step 2:** Implement any missing Debug controls, then run `scripts/check-ios.sh`. Expected:
  PASS.
- [ ] **Step 3:** Update the docs (`docs/ios.md`: E2E controls). Run the part checkpoint, then
  commit, push and open the PR.

---

## Part F: home and profile (`feat/ios-home-profile`)

### Task F1: home

**Files:**
- Create: `ios/WhitelabelKit/Sources/WhitelabelKit/Features/Home/Home.swift`, `HomeScreen.swift`
- Modify: `ShowcaseApp.swift` (add `Home(shortcuts:)` with showcase's shortcuts, `startTab: home`)
- Test: `ios/Showcase/ShowcaseUITests/HomeUITests.swift` + `Robots/HomeRobot.swift`

**Interfaces:**
- Produces: `struct Home: Feature { init(shortcuts: [HomeShortcut]) }`, with
  `struct HomeShortcut { let title: LocalizedStringKey; let systemImage: String; let route: String }`.
  Shortcuts switch to the tab with that route. The screen shows the greeting (`UiTextView`), pull
  to refresh, and the refresh error message.
- [ ] **Step 1: Write the failing tests:**
  - `HomeUITests.greetsByFirstName` ("Hello, Alice").
  - `HomeUITests.shortcutOpensSettings`.
  - `HomeUITests.refreshOfflineShowsTheMessage`: the shared offline message. The fake goes offline
    through `showcase://_fake/offline`.
- [ ] **Step 2:** Run. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run. Expected: PASS.
- [ ] **Step 5:** Commit `feat(ios): add the home screen`.

### Task F2: profile and change password, and Part F checkpoint

**Files:**
- Create: `ios/WhitelabelKit/Sources/WhitelabelKit/Features/Profile/Profile.swift`,
  `ProfileScreen.swift`, `ChangePasswordScreen.swift`
- Modify: `ShowcaseApp.swift` (add `Profile()`)
- Test: `ios/Showcase/ShowcaseUITests/ProfileUITests.swift` + `Robots/ProfileRobot.swift`

- [ ] **Step 1: Write the failing tests:**
  - `ProfileUITests.editNameAndHomeGreetsTheNewName`.
  - `ProfileUITests.wrongCurrentPasswordStaysSignedIn`: the shared message "The current password
    is wrong.".
  - `ProfileUITests.tooShortShowsTheMinimum`: "Use at least 10 characters.".
- [ ] **Step 2:** Run. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run. Expected: PASS.
- [ ] **Step 5:** Commit `feat(ios): add the profile screens`. Run the part checkpoint, push and
  open the PR.

---

## Part G: catalog and parity (`feat/ios-catalog`)

### Task G1: the catalog list and detail

**Files:**
- Create: `ios/WhitelabelKit/Sources/WhitelabelKit/Features/Catalog/Catalog.swift`,
  `CatalogListScreen.swift`, `CatalogDetailScreen.swift`, `CatalogImage.swift` (`AsyncImage` with
  placeholder and failure states)
- Modify: `ShowcaseApp.swift` (add `Catalog(categories: ["Books", "Games", "Music", "Travel"])`,
  the same as Android's showcase)
- Test: `ios/Showcase/ShowcaseUITests/CatalogUITests.swift` + `Robots/CatalogRobot.swift`

**Interfaces:**
- Produces: `struct Catalog: Feature { init(categories: [String] = [], pageSize: Int32 = 20, defaultSort: CatalogSort = .newest) }`.
  It builds `CatalogOptions` (Kotlin validates it). The list does search (`.searchable`), category
  chips, the sort menu, paging on scroll to the end, and retry. The detail is by id.
- [ ] **Step 1: Write the failing tests:**
  - `CatalogUITests.searchFilterSortAndOpenAnItem`, mirroring Android's
    `catalog_searchFilterSortAndOpenAnItem`.
  - `CatalogUITests.switchedOffHidesTabAndShortcut`: `-fakeFeatures catalog=false`.
- [ ] **Step 2:** Run. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run. Expected: PASS.
- [ ] **Step 5:** Commit `feat(ios): add the catalog screens`.

### Task G2: catalog deep link, parity check and final docs

- [ ] **Step 1: Write the failing test:**
  `CatalogUITests.catalogLinkWhileSignedOutOpensAfterSignIn` (Review Focus 2): open
  `showcase://catalog/<id of item 7>` signed out, sign in, and the detail shows "Sample item 7".
- [ ] **Step 2:** Implement the catalog deep-link mapping. Run `scripts/check-ios.sh`. Expected:
  PASS.
- [ ] **Step 3: Parity check.**
  - List every test in Android's `ShowcaseE2ETest` and `CleanStartTest`, each with its iOS
    counterpart.
  - The only allowed gaps are request-log assertions (spec §9).
  - Put the table in the PR.
- [ ] **Step 4: Docs.**
  - `docs/ios.md` complete.
  - `docs/superpowers/specs/2026-11-11-ios-kmp-stack-design.md` §4: step 5 done, with PR numbers.
  - `README.md`: iOS mention.
- [ ] **Step 5:** Run the part checkpoint, then commit, push and open the PR.
