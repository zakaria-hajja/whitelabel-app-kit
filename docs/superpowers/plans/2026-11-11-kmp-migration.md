# KMP Migration (iOS steps 1–4) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the foundation to Kotlin Multiplatform up to and including the feature ViewModels.
Android keeps Jetpack Compose and behaves exactly as before, and every shared module compiles and
tests on iOS.

**Architecture:**
- **Four parts, one branch and one PR each.** Android stays green after every task.
  - **A:** KMP build setup and two pilot modules.
  - **B:** Hilt → Koin, still Android-only.
  - **C:** core modules → KMP.
  - **D:** feature logic and ViewModels → KMP.
- **Feature split.** Each feature splits into an Android UI module and a KMP logic module:
  - `:feature:<name>` keeps its path and stays a plain Android library: Compose screens, the
    `AppDefinitionBuilder.<name>()` entry, `R.string` screen copy, `androidTest`.
  - The new `:feature:<name>:shared` (KMP) holds the feature's domain, data, ViewModels,
    State/Action/Event and UI models.
  - The same pattern is used for `:core:reminders` (`:core:reminders:shared`). `:core:presentation`
    (new, KMP) holds the shared `UiText`.
- **KMP library targets:** `androidTarget` via `com.android.kotlin.multiplatform.library`,
  `iosArm64`, `iosSimulatorArm64`, and `jvm()`. The JVM target ships nowhere. It exists so host
  tests run on plain JUnit and so the JVM-only `:core:testing` (contract tests) can still be used
  from `jvmTest`.
- **Koin replaces Hilt:**
  - Each module exposes a `val <name>Module: Module`.
  - Each feature's DSL function adds its module to its `FeatureEntry`.
  - `FoundationApplication` starts Koin from the app's definition.
  - The mock backend is still switched on just by being on the classpath, now through
    `java.util.ServiceLoader`.

**Tech Stack:**
- Existing (unchanged): Kotlin 2.2.20, AGP 8.13.2, Gradle 8.13, Compose BOM 2025.10.01, Ktor 3.3.1,
  kotlinx-serialization 1.9.0, coroutines 1.10.2, DataStore 1.1.7, lifecycle 2.9.4, Turbine, AssertK.
- New:

  | Library | Version |
  |---|---|
  | Koin BOM (`koin-core`, `koin-android`, `koin-androidx-compose`, `koin-test`) | `4.1.1` |
  | Compose Multiplatform plugin and `components-resources` | `1.9.1` |
  | `kotlinx-datetime` | `0.7.1` |
  | `okio` + `okio-fakefilesystem` | `3.16.2` |
  | `androidx.datastore:datastore-core-okio` | `1.1.7` |
  | `ktor-client-darwin` | `3.3.1` |
  | `androidx.lifecycle:lifecycle-viewmodel` + `lifecycle-viewmodel-savedstate` (KMP artifacts) | `2.9.4` |
  | `kotlin-test` | Kotlin's version |
  | `kotlinx-atomicfu` | `0.29.0` |

  If a pinned version fails to resolve or is incompatible with Kotlin 2.2.20, Task A1 picks the
  newest compatible one and records it in the PR. It does **not** change the Kotlin version.

**Spec:** `docs/superpowers/specs/2026-11-11-ios-kmp-stack-design.md`. This plan covers §3.1,
§3.3 and §4 steps 1–4. Step 0 (removing globex) is already merged (#18).

**Out of scope:**
- The iOS starter, SwiftUI, SKIE and XCFramework delivery (step 5, its own plan).
- Munch (step 6).
- The shell's `ShellViewModel`/`BrandPreviewViewModel` and `AppDefinition` stay Android. Extracting
  a platform-neutral shell model is the first task of the iOS starter plan, because its shape
  depends on the SwiftUI shell.
- iOS implementations of `TokenCipher` (CryptoKit + Keychain) and `LanguageRepository`. These are
  written in Swift by the iOS starter and handed to Koin; this plan only defines the seams.
- Part D of the Phase 5 plan (`ci/instrumented`), postponed until after this migration.

## Decisions this plan makes (owner: please confirm in review)

1. **Split feature modules (UI module + `:shared`), not one KMP module with an `androidMain` UI.**
   This keeps the Android UI on the standard Android library plugin: build variants,
   `debugImplementation` tooling, `androidTest`, `R`. App modules and app repos keep depending on
   `:feature:<name>` unchanged. The UI module depends on its shared module with `api`.
2. **A `jvm()` target on every KMP library.** It's cheap, makes host tests plain JUnit 5, and lets
   `:core:testing` stay a JVM module that `jvmTest` depends on. `commonTest` runs on JVM and iOS;
   `androidDeviceTest` is only for code that needs a `Context`/Keystore.
3. **Strings used by shared code are Compose Multiplatform resources** (`Res.string.*`, typed),
   following the `kmp-presentation-mvi` skill.
   - `UiText.Resource` holds a `StringResource`. Android UI calls the composable
     `UiText.asString()`. Swift will call `suspend fun UiText.asStringAsync()`.
   - Strings only screens use stay in `R.string` (and later in iOS `Localizable.xcstrings`).
   - This is the design doc's "builder's call" for resources, decided here. It means messages from
     ViewModels have one translation source on both platforms.
4. **Koin replaces Hilt in one flip (Part B).** Koin modules are written first and checked with
   `verify()` while Hilt still runs, then one task switches the app over and deletes Hilt. No
   period with both graphs live at runtime.
5. **The mock backend stays "on the classpath is the switch".** `:core:network` declares
   `ModuleContributor`. `:core:mockapi` registers one in `META-INF/services`, and the Android shell
   loads every contributor with `ServiceLoader` at start. Release builds still can't contain it
   (unchanged `verify-apps.sh` check).
6. **E2E tests restart Koin per test.** `HiltAndroidRule`, `ResetAppDataRule` and `EndAppScopeRule`
   are replaced by one `FreshAppRule`: stop Koin and join the app scope, delete stored data, start
   Koin. The real app class runs (no test application).
7. **No change to where Android stores data.** `JsonStore` files stay at
   `filesDir/datastore/<name>.json`, and the Keystore alias stays `foundation_session`.

## Global Constraints

- Everything in the Phase 4 and Phase 5 plans' Global Constraints still holds:
  - versions only from `gradle/libs.versions.toml`;
  - no app identity in shared code;
  - strings in English and French;
  - passwords never logged or sent to analytics;
  - release builds never contain `com/whitelabel/core/mockapi`.
- **Logic stays out of platform code** (spec anti-goal). Nothing that computes, validates or
  formats moves into `androidMain`. Platform source sets hold only adapters behind interfaces.
- **Public API of every `:shared` module is Swift-ready** (spec §3.3):
  - ViewModels expose `val state: StateFlow<State>`, `fun onAction(action: Action)`, and, where
    events exist, `val events: Flow<Event>`.
  - Actions and events are sealed interfaces.
  - No default arguments on constructors Swift will call.
  - ViewModels, State, Action, Event and UI models become `public` (they were `internal`; the UI
    module is now another module). Data sources, DTOs and mappers stay `internal`.
- **Package names stay the same** when code moves (`com.whitelabel.feature.settings.presentation`
  etc.). Only the module changes.
- **New modules**, all added to `gradle/foundation.settings.gradle.kts`:

  | Module | Plugin | Namespace / package |
  |---|---|---|
  | `:core:presentation` | `whitelabel.kmp.library` + `whitelabel.kmp.resources` | `com.whitelabel.core.presentation` |
  | `:core:reminders:shared` | `whitelabel.kmp.library` | `com.whitelabel.core.reminders` |
  | `:feature:<name>:shared` for authentication, settings, profile, home, catalog | `whitelabel.kmp.library` + `whitelabel.kmp.resources` | `com.whitelabel.feature.<name>` (namespace `com.whitelabel.feature.<name>.shared`) |
  | `:apps:showcase:shared` (Task D6) | `whitelabel.kmp.library` | `com.whitelabel.apps.showcase.shared` |
- **Tests:**
  - `commonTest` uses `kotlin.test`, AssertK, Turbine and `kotlinx-coroutines-test`.
  - JUnit 5 annotations translate as `@BeforeEach`→`@BeforeTest`, `@AfterEach`→`@AfterTest`.
  - `@TempDir` tests use okio `FakeFileSystem` in `commonTest`, or stay in `jvmTest`.
  - Tests that use `:core:testing` (contract tests) live in `jvmTest`.
  - Every moved test keeps its name and assertions. A test only changes source set and framework.
- **Git** follows `CLAUDE.md`, with one branch and one PR per part:

  | Part | Branch | Precondition |
  |---|---|---|
  | A | `chore/kmp-build` | — |
  | B | `chore/hilt-to-koin` | `feat/settings-sections` merged (it edits `:feature:settings`) |
  | C | `chore/kmp-core` | B merged |
  | D | `chore/kmp-features` | C merged |
- **Part checkpoint** (end of each part):
  1. `scripts/check.sh` (on the Mac this includes `iosSimulatorArm64Test` and `jvmTest`).
  2. `./gradlew assembleDebug assembleRelease`.
  3. `scripts/verify-apps.sh`, plus `-Pwhitelabel.mockApi=true assembleRelease` and
     `scripts/verify-apps.sh release`.
  4. The standalone fixture build, as in CI.
  5. `./gradlew :apps:showcase:connectedDebugAndroidTest`, plus `connected…AndroidTest` for the
     touched Android modules and `connectedAndroidDeviceTest` for the touched KMP modules, on an
     emulator nothing else is using.
  6. Update the docs listed in the part's last task.
  7. Open the PR from the template, wait for CI green, and report. The owner merges.

## Review Focus

1. **A missing Koin binding shows up only at runtime** (Hilt caught it at compile time). Opening any
   screen must never crash. Pinned in Task B1 (`verify()` per module), Task B3 (a showcase test that
   starts the full graph and resolves every ViewModel) and Task D6 (the same graph on iOS).
2. **Arguments and process death through `SavedStateHandle` under Koin.** The reset token from a
   deep link, the pending deep link held until sign-in, and a catalog item id must reach their
   ViewModels after `koinViewModel()`. Pinned in Task B3: an instrumented test opens
   `showcase://reset-password?token=t1` and asserts the reset screen got `t1`, and the existing E2E
   deep-link flows stay green.
3. **E2E tests leaking state between tests once Koin is restarted.** A second test must not see the
   first one's session, fake backend or open DataStore file ("only one store per file"). Pinned in
   Task B3: `CleanStartTest` runs two tests that each assert a signed-out start and a fresh
   `FakeBackend` instance.
4. **Stored text written by `java.time` read by `kotlinx-datetime`.** Reminders persist
   `LocalDateTime.toString()`, and a reminder stored before the migration must still fire. Pinned in
   Task C5 with fixtures `"2026-11-11T09:00"`, `"2026-11-11T09:00:30"` and
   `"2026-11-11T09:00:30.123456789"`.
5. **Language tags without `java.util.Locale`.** `fr-CA`→`fr`, `zh-Hant-TW`→`zh`, `en_US`→`en`,
   and an empty string → `null` (follow the device). Pinned in Task D3.

---

## Part A: KMP build setup (`chore/kmp-build`)

### Task A1: KMP convention plugin and the `:core:common` pilot

**Files:**
- Modify: `gradle/libs.versions.toml` (versions/libraries/plugins from the Tech Stack table;
  plugins `kotlin-multiplatform`, `android-kotlin-multiplatform-library`, `compose-multiplatform`)
- Modify: `build.gradle.kts` (root: add the new plugins `apply false`)
- Modify: `fixtures/standalone-app/build.gradle.kts` and `CLAUDE.md`'s note (app repos copy the
  root plugin list)
- Modify: `gradle.properties` (`kotlin.native.ignoreDisabledTargets=true`, so Linux CI skips iOS
  quietly)
- Modify: `build-logic/convention/build.gradle.kts` (register `kmpLibrary`; `compileOnly` the
  compose-multiplatform plugin artifact)
- Create: `build-logic/convention/src/main/kotlin/com/whitelabel/buildlogic/KmpLibraryConventionPlugin.kt`
- Create: `build-logic/convention/src/main/kotlin/com/whitelabel/buildlogic/KotlinMultiplatform.kt`
- Create: `build-logic/convention/src/test/kotlin/com/whitelabel/buildlogic/KmpNamespaceTest.kt`
- Modify: `core/common/build.gradle.kts`; move `core/common/src/main/kotlin` → `src/commonMain/kotlin`
  and `src/test/kotlin` → `src/commonTest/kotlin`

**Interfaces:**
- Produces:
  - Plugin id `whitelabel.kmp.library`.
  - `internal fun kmpNamespace(projectPath: String): String`. It maps `:core:common` →
    `com.whitelabel.core.common` and `:feature:settings:shared` →
    `com.whitelabel.feature.settings.shared` (lowercase, `-` removed, so
    `:core:testing-android` → `com.whitelabel.core.testingandroid`).

- [ ] **Step 1: Write the failing build-logic test** `KmpNamespaceTest`:
  `kmpNamespace(":core:common") == "com.whitelabel.core.common"`,
  `kmpNamespace(":feature:settings:shared") == "com.whitelabel.feature.settings.shared"`,
  `kmpNamespace(":apps:showcase:shared") == "com.whitelabel.apps.showcase.shared"`,
  `kmpNamespace(":core:reminders-x") == "com.whitelabel.core.remindersx"`.
- [ ] **Step 2: Run** `./gradlew -p build-logic test --tests '*KmpNamespaceTest*'`. Expected:
  FAIL (unresolved `kmpNamespace`).
- [ ] **Step 3: Implement `KmpLibraryConventionPlugin`**:
  - Apply `com.android.kotlin.multiplatform.library`, `org.jetbrains.kotlin.multiplatform` and
    `org.jetbrains.kotlin.plugin.serialization`.
  - `androidLibrary`: `compileSdk = COMPILE_SDK`, `minSdk = MIN_SDK`,
    `namespace = kmpNamespace(path)`, `withDeviceTestBuilder { sourceSetTreeName = "test" }` with
    runner `androidx.test.runner.AndroidJUnitRunner`.
  - Targets: `iosArm64()`, `iosSimulatorArm64()`, `jvm()`.
  - `jvmToolchain(JDK_VERSION)` and `applyDefaultHierarchyTemplate()`.
  - Dependencies:
    - `commonMain`: `kotlinx-coroutines-core`, `kotlinx-serialization-json`.
    - `commonTest`: `kotlin-test`, `assertk`, `turbine`, `kotlinx-coroutines-test`.
    - `jvmTest`: `kotlin-test-junit5`, `junit-jupiter`, `junit-platform-launcher` (runtime).
  - `jvmTest` tasks `useJUnitPlatform()`.
  - Call the existing `pointTestsAtTheContract()`; make it `internal` and reuse it.
- [ ] **Step 4: Run** the build-logic test again. Expected: PASS.
- [ ] **Step 5: Convert `:core:common`.**
  - Apply `whitelabel.kmp.library`, move the sources, and turn its 81 lines of tests into
    `kotlin.test`. No production change.
  - Anything that depended on `:core:common` keeps working: Android modules resolve the `android`
    variant, `:core:testing` (JVM) the `jvm` one.
- [ ] **Step 6: Run** `./gradlew :core:common:allTests :core:testing:test assembleDebug`. Expected:
  PASS, with `jvmTest` and `iosSimulatorArm64Test` both listed.
- [ ] **Step 7: Commit** `build(kmp): add the kmp library convention and move core:common`.

### Task A2: Shared strings: `whitelabel.kmp.resources` and `:core:presentation`

**Files:**
- Create: `build-logic/convention/src/main/kotlin/com/whitelabel/buildlogic/KmpResourcesConventionPlugin.kt`
- Create: `core/presentation/build.gradle.kts`
- Create: `core/presentation/src/commonMain/kotlin/com/whitelabel/core/presentation/UiText.kt`
- Create: `core/presentation/src/commonMain/kotlin/com/whitelabel/core/presentation/DataErrorText.kt`
- Create: `core/presentation/src/commonMain/composeResources/values/strings.xml` and
  `values-fr/strings.xml`. Copy the `core_ui_error_*` strings from `core/ui/src/main/res`, keeping
  names and texts. `core:ui`'s copy is deleted in Task D6.
- Create: `core/presentation/src/commonTest/kotlin/com/whitelabel/core/presentation/DataErrorTextTest.kt`
- Create: `core/presentation/src/androidDeviceTest/kotlin/com/whitelabel/core/presentation/UiTextAndroidTest.kt`
- Modify: `gradle/foundation.settings.gradle.kts` (add `:core:presentation`)

**Interfaces:**
- Consumes: `whitelabel.kmp.library` (A1).
- Produces:
  - Plugin `whitelabel.kmp.resources`: applies `org.jetbrains.compose`,
    `org.jetbrains.kotlin.plugin.compose` and `components-resources`; sets
    `experimentalProperties["android.experimental.kmp.enableAndroidResources"] = true`;
    `compose.resources { publicResClass = true; packageOfResClass = "<namespace>.resources" }`.
  - `UiText`:
    ```kotlin
    sealed interface UiText {
        data class Dynamic(val value: String) : UiText
        class Resource(val id: StringResource, val args: List<Any> = emptyList()) : UiText
    }
    @Composable fun UiText.asString(): String
    suspend fun UiText.asStringAsync(): String
    ```
  - `fun DataError.toUiText(): UiText`, with the same mapping as
    `core/ui/.../DataErrorText.kt` today.

- [ ] **Step 1: Write the failing test** `DataErrorTextTest`. Port every case of
  `core/ui`'s current `DataError.toUiText` tests. Each asserts the `Resource.id` equals the matching
  `Res.string.core_ui_error_*`, and `RATE_LIMITED` with `retryAfterSeconds = 30` carries
  `args == listOf(30)`. Add `asStringAsync_resolvesEnglish`:
  `UiText.Resource(Res.string.core_ui_error_no_internet).asStringAsync()` equals the English text,
  run on JVM and iOS.
- [ ] **Step 2: Run** `./gradlew :core:presentation:allTests`. Expected: FAIL (module/types missing).
- [ ] **Step 3: Implement** the plugin, the module, `UiText` and `DataError.toUiText()`.
- [ ] **Step 4: Run** `./gradlew :core:presentation:allTests`. Expected: PASS on `jvmTest` and
  `iosSimulatorArm64Test`.
- [ ] **Step 5: Write the Android proof** `UiTextAndroidTest.resolvesFrenchOnAndroid`. Under a
  `fr` locale, `asStringAsync()` returns the French text of `core_ui_error_no_internet`.
- [ ] **Step 6: Run** `./gradlew :core:presentation:connectedAndroidDeviceTest`. Expected: PASS.
- [ ] **Step 7: Commit** `build(kmp): share ui strings through compose resources`.

### Task A3: Part A checkpoint and docs

**Files:**
- Modify: `docs/architecture.md`: module table rows for `:core:common` (KMP) and
  `:core:presentation`, and a "Kotlin Multiplatform" section covering the targets, the source sets
  and why `jvm()` exists.
- Modify: `docs/testing.md`: `allTests`, `commonTest`/`jvmTest`/`androidDeviceTest`, and iOS tests
  running only on macOS.
- Modify: `scripts/check.sh`: a one-line comment that on macOS `check` includes the iOS simulator
  tests.

- [ ] **Step 1:** Write the docs.
- [ ] **Step 2:** Run the part checkpoint. Expected: all green. On Ubuntu CI, iOS tasks are
  skipped with no error.
- [ ] **Step 3:** Commit `docs(kmp): document targets, source sets and ios tests`, then push and
  open the PR.

---

## Part B: Hilt → Koin (`chore/hilt-to-koin`)

The app is still Android-only in this part. Koin modules live in each module's `src/main` for now,
written with no Android types except in the modules named `android…Module`, so Parts C and D can
move them to `commonMain` unchanged.

### Task B1: Koin modules for the core layers, checked while Hilt still runs

**Files:**
- Modify: `gradle/libs.versions.toml` (Koin BOM, `koin-core`, `koin-android`,
  `koin-androidx-compose`, `koin-test`)
- Create: `build-logic/convention/src/main/kotlin/com/whitelabel/buildlogic/KoinConventionPlugin.kt`
  (id `whitelabel.koin`): adds the Koin BOM and `koin-core`; on Android modules also `koin-android`;
  with Compose also `koin-androidx-compose`; on KMP modules adds `koin-core` to `commonMain`. Adds
  `koin-test` to the test configuration.
- Create: `core/network/src/main/kotlin/com/whitelabel/core/network/di/NetworkKoin.kt`
- Create: `core/network/src/main/kotlin/com/whitelabel/core/network/ModuleContributor.kt`
- Create: `core/data/src/main/kotlin/com/whitelabel/core/data/di/DataKoin.kt`
- Create: `core/datastore/src/main/kotlin/com/whitelabel/core/datastore/StorePaths.kt`
- Create: `core/mockapi/src/main/kotlin/com/whitelabel/core/mockapi/MockApiKoin.kt`
- Create: `core/reminders/src/main/kotlin/com/whitelabel/core/reminders/RemindersKoin.kt`
- Test: `core/network/src/test/.../di/NetworkKoinTest.kt`, `core/data/src/test/.../di/DataKoinTest.kt`,
  `core/mockapi/src/test/.../MockApiKoinTest.kt`, `core/reminders/src/test/.../RemindersKoinTest.kt`

**Interfaces:**
- Produces:
  - `val networkModule: Module`: `single<HttpClient> { HttpClientFactory.create(get(), get(), get()) }`.
  - `fun interface ModuleContributor { fun modules(): List<Module> }`, with KDoc saying it is loaded
    with `ServiceLoader` by the Android shell, and that this is how classpath-switched modules add
    bindings.
  - `val ApplicationScope: Qualifier = named("applicationScope")`, in `core.data.di`, replacing the
    annotation.
  - `fun interface StorePaths { fun file(name: String): File }`. Part C changes the return type to
    `okio.Path`.
  - `val dataModule: Module`:
    - the application scope single, `CoroutineScope(SupervisorJob() + Dispatchers.Default)`, with
      `onClose { it?.cancel() }`;
    - the four repositories and `SessionTokenStore` bound to their interfaces as singles;
    - `TokenStore`, `UserCache`, `AppConfigCache` and `UserPreferencesRepository` built from
      `get<StorePaths>().file("session" | "user" | "app_config" | "preferences")`.
  - `val androidDataModule: Module`: `StorePaths { Context.jsonStoreFile(it) }`,
    `TokenCipher` → `KeystoreTokenCipher()`, `LanguageRepository` → `AppCompatLanguageRepository`.
  - `val mockApiModule: Module`: `single { FakeBackend() }`,
    `single<EngineOverride> { EngineOverride { get<FakeBackend>().engine } }`.
  - `class MockApiContributor : ModuleContributor`, returning `listOf(mockApiModule)`.
  - `fun remindersModule(config: ReminderConfig? = null): Module`. It provides `Reminders`, built
    exactly as `RemindersModule.reminders` does today (sets `ReminderConfigProvider.config`,
    `ensureChannels`, `rearmOnce()`).

- [ ] **Step 1: Write the failing tests.** Each Koin test is a JUnit 5 test calling
  `module.verify(extraTypes = listOf(...))` (`@OptIn(KoinExperimentalAPI::class)`):
  - `networkModule` with `HttpClientEngine`, `NetworkConfig`, `SessionTokenStore`;
  - `dataModule + androidDataModule` with `Context`, `HttpClient`, `BrandConfig`;
  - `mockApiModule` with none;
  - `remindersModule()` with `Context`.

  Add `DataKoinTest.applicationScopeIsCancelledOnClose`: start a `koinApplication` with
  `dataModule` and a fake `StorePaths`/`TokenCipher`/`LanguageRepository`, read
  `get<CoroutineScope>(ApplicationScope)`, call `close()`, and assert `!scope.isActive`.
- [ ] **Step 2: Run** `./gradlew :core:network:test :core:data:testDebugUnitTest :core:mockapi:testDebugUnitTest :core:reminders:testDebugUnitTest`.
  Expected: FAIL (modules not defined).
- [ ] **Step 3: Implement** the Koin files. Apply `whitelabel.koin` next to `whitelabel.hilt` in the
  four modules. Leave the Hilt modules in place and in use.
- [ ] **Step 4: Run** the same command. Expected: PASS. `./gradlew assembleDebug` is still green.
- [ ] **Step 5: Commit** `refactor(di): define koin modules for network, data, mockapi, reminders`.

### Task B2: Koin modules for the features and the shell, registered through the definition

**Files:**
- Modify: `core/shell/src/main/kotlin/com/whitelabel/core/shell/AppDefinition.kt`
- Create: `core/shell/src/main/kotlin/com/whitelabel/core/shell/brand/ShellKoin.kt`
- Create: `feature/<name>/src/main/kotlin/com/whitelabel/feature/<name>/<Name>Koin.kt` for
  authentication, settings, profile, home, catalog
- Modify: each feature's DSL file (`Settings.kt`, `Auth…`, `Profile.kt`, `Home.kt`, `Catalog.kt`)
  so its `FeatureEntry` passes `modules = listOf(<name>Module)`
- Test: `core/shell/src/test/.../AppDefinitionModulesTest.kt`, `core/shell/src/test/.../ShellKoinTest.kt`,
  and `feature/<name>/src/test/.../<Name>KoinTest.kt` for each feature

**Interfaces:**
- Consumes: B1's modules and `ModuleContributor`.
- Produces:
  - `FeatureEntry(..., modules: List<Module> = emptyList(), routes = ...)`, a new parameter placed
    before `routes`.
  - `AppDefinitionBuilder.modules(vararg modules: Module)`, for an app's own bindings (e.g. Munch's
    repositories, `remindersModule(config)`).
  - `internal val AppDefinition.koinModules: List<Module>`: the app's modules followed by every
    entry's modules, in definition order, with no duplicates.
  - `internal fun shellModule(definition: AppDefinition): Module`. It provides:
    - `AppDefinition`, and `BrandConfig` (from resources);
    - `NetworkConfig`, same values as `ShellModule` today;
    - `HttpClientEngine` = `getOrNull<EngineOverride>()?.engine() ?: OkHttp.create()`;
    - `named("usingMockApi")` Boolean;
    - `Analytics` and `CrashReporter`, same defaults as today;
    - `HealthDataSource` → `KtorHealthDataSource`;
    - `viewModelOf(::ShellViewModel)` and `viewModelOf(::BrandPreviewViewModel)`.
  - `internal fun foundationModules(definition: AppDefinition, contributors: List<ModuleContributor>): List<Module>`.
    It returns `networkModule`, `dataModule`, `androidDataModule`, `shellModule(definition)`,
    `definition.koinModules` and every contributor's modules, in that order.
  - Feature modules:
    - `viewModelOf(::XViewModel)` for each ViewModel;
    - catalog list as `viewModel { (options: CatalogOptions) -> CatalogListViewModel(options, get(), get()) }`;
    - each data source bound to its interface as a single.

- [ ] **Step 1: Write the failing tests.**
  - `AppDefinitionModulesTest`:
    - `entryModulesFollowAppModulesInOrder`;
    - `sameModuleFromTwoEntriesAppearsOnce`;
    - `definitionWithoutModulesHasNone`.
  - `ShellKoinTest.foundationGraphVerifies`: `foundationModules(showcaseLikeDefinition, emptyList())`
    `.verify(extraTypes = listOf(Context::class, SavedStateHandle::class, CatalogOptions::class))`.
  - `ShellKoinTest.engineIsTheOverrideWhenAContributorBindsOne`: with `MockApiContributor`, the
    resolved `HttpClientEngine` is a `MockEngine` and `usingMockApi` is true. Without it, the
    engine is OkHttp and the flag is false.
  - Each `<Name>KoinTest`: `<name>Module.verify(...)` with the core types as extras.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*Koin*' --tests '*AppDefinitionModules*'`.
  Expected: FAIL.
- [ ] **Step 3: Implement.** Still no runtime change: Hilt drives the app.
- [ ] **Step 4: Run** the same command. Expected: PASS.
- [ ] **Step 5: Commit** `refactor(di): register feature and shell koin modules in the definition`.

### Task B3: Switch the app to Koin and delete Hilt

**Files:**
- Modify: `core/shell/src/main/kotlin/com/whitelabel/core/shell/FoundationApplication.kt`
- Modify: `core/shell/src/main/kotlin/com/whitelabel/core/shell/FoundationActivity.kt`
  (`by viewModels()` → `by viewModel()`)
- Modify: every `*Screen.kt`/`*Root` (`hiltViewModel()` → `koinViewModel()`; catalog list
  `koinViewModel { parametersOf(options) }`)
- Modify: every ViewModel, data source and repository: drop `@HiltViewModel`, `@Inject`,
  `@AssistedInject`/`@Assisted`/`@AssistedFactory`, and `@ApplicationScope` (use the qualifier in
  the module)
- Delete: `DataModule.kt`, `ApplicationScope.kt` (annotation), `NetworkModule.kt`, `ShellModule.kt`,
  `EngineModule.kt` (keep `selectEngine` only if a test still needs it, else delete),
  `DiagnosticsModule.kt`, `MockApiModule.kt`, `RemindersModule.kt` and each feature's `*DataModule.kt`
- Create: `core/mockapi/src/main/resources/META-INF/services/com.whitelabel.core.network.ModuleContributor`
  (content: `com.whitelabel.core.mockapi.MockApiContributor`)
- Modify: `apps/showcase/src/main/kotlin/.../ShowcaseApp.kt` and `ShowcaseDefinition.kt`, and
  `fixtures/standalone-app/app/src/main/kotlin/.../FixtureApp.kt` and `FixtureDefinition.kt`
- Modify: `core/testing-android`: delete `FoundationTestRunner.kt`, `EndAppScopeRule.kt` and
  `ResetAppDataRule.kt`; create `FreshAppRule.kt`
- Modify: `build-logic/.../AndroidApplicationConventionPlugin.kt`:
  - apply `whitelabel.koin` instead of `whitelabel.hilt`;
  - runner `androidx.test.runner.AndroidJUnitRunner`;
  - remove `hilt-android-testing` and `kspAndroidTest`.
- Delete: `build-logic/.../HiltConventionPlugin.kt` and its registration. Replace
  `id("whitelabel.hilt")` with `id("whitelabel.koin")` in every module. Remove Hilt, KSP and
  `androidx-hilt-navigation-compose` from the catalog and root plugins.
- Modify: `apps/showcase/src/androidTest/...` (`CleanStartTest.kt`, `ShowcaseE2ETest.kt`) and the
  modules' own `androidTest`s that used Hilt
- Create: `apps/showcase/src/androidTest/kotlin/com/whitelabel/apps/showcase/GraphTest.kt`
- Create: `apps/showcase/src/androidTest/kotlin/com/whitelabel/apps/showcase/ResetDeepLinkTest.kt`

**Interfaces:**
- Consumes: B1, B2.
- Produces:
  - `abstract class FoundationApplication : Application()`:
    - `abstract fun definition(): AppDefinition`;
    - `fun startFoundation()` calls `startKoin { androidContext(this); modules(foundationModules(definition(), ServiceLoader.load(ModuleContributor::class.java).toList())) }`;
    - `fun stopFoundation()` gets the application scope's `Job`, calls `stopKoin()`, then cancels
      and joins the job (`runBlocking`);
    - `onCreate()` calls `startFoundation()` and keeps the crash-handler behavior, resolving
      `CrashReporter` from Koin lazily inside the handler.
  - Apps: `class ShowcaseApp : FoundationApplication() { override fun definition() = showcaseDefinition() }`.
    `ShowcaseDefinition` becomes the top-level `fun showcaseDefinition(): AppDefinition`, with the
    same contents.
  - `class FreshAppRule(private val keystoreAlias: String = "foundation_session") : TestRule`.
    Before each test it runs `stopFoundation()`, deletes `filesDir/datastore` and the Keystore
    alias, then runs `startFoundation()`.
  - E2E tests get the fake per use (`GlobalContext.get().get<FakeBackend>()`) and never cache it
    across tests.

- [ ] **Step 1: Write the failing instrumented tests** in `apps/showcase/src/androidTest`.
  - `GraphTest.everyViewModelResolves`: resolve each of the 12 ViewModel classes (an explicit list
    in the test: shell, brand preview, the 4 auth, settings, home, the 2 catalog, the 2 profile)
    through a `ViewModelStoreOwner` with `koinViewModel`'s resolver. Pass `CatalogOptions()` to the
    catalog list. Assert each is non-null.
  - `ResetDeepLinkTest.resetLinkReachesTheResetScreen`: launch `FoundationActivity` with
    `showcase://reset-password?token=t1`. The reset screen is shown, and submitting a new password
    sends `t1` to the fake. Assert with `FakeBackend.lastResetToken`; add that read-only accessor
    to `FakeBackend` if it doesn't exist.
  - In `CleanStartTest`: `firstTestStartsSignedOut` and `secondTestStartsSignedOut`. Each asserts
    the sign-in screen is shown and that `FakeBackend` has no sessions. A `companion` remembers the
    first test's `FakeBackend` identity hash, and the second asserts it's a different instance.
- [ ] **Step 2: Run** `./gradlew :apps:showcase:connectedDebugAndroidTest`. Expected: FAIL to
  compile (new APIs missing).
- [ ] **Step 3: Implement the switch** (the files above). Keep each screen's behavior unchanged:
  this task changes only how objects are created.
- [ ] **Step 4: Run**:
  - `./gradlew check assembleDebug assembleRelease`;
  - `./gradlew :apps:showcase:connectedDebugAndroidTest` plus every module's `connectedDebugAndroidTest`;
  - `grep -rE "dagger|javax.inject|hilt" --include=*.kts --include=*.kt --include=*.toml .`

  Expected: all green, and the grep returns nothing (outside `build/` and `docs/`).
- [ ] **Step 5: Commit** `refactor(di): replace hilt with koin`.

### Task B4: Part B checkpoint and docs

**Files:**
- Modify: `docs/architecture.md` (DI section: Koin, module per layer, definition-registered
  feature modules, `ModuleContributor`)
- Modify: `docs/creating-an-app.md` (`FoundationApplication.definition()`, no `@HiltAndroidApp`,
  `modules(...)` for app bindings)
- Modify: `docs/adding-a-shared-feature.md` (a feature ships `<name>Module` and passes it in its
  `FeatureEntry`; `koinViewModel()`; `SettingsSection` content may use `koinViewModel()`)
- Modify: `docs/creating-a-standalone-app.md` (what an app repo changes when it bumps past this PR)
- Modify: `docs/testing.md` (`FreshAppRule`, Koin `verify()` tests)
- Modify: `core/reminders/.../Reminders.kt` KDoc ("Inject it with Hilt" → "Get it from Koin; add
  `remindersModule(config)` to the definition")

- [ ] **Step 1:** Write the docs.
- [ ] **Step 2:** Run the part checkpoint. Expected: all green.
- [ ] **Step 3:** Commit `docs(di): document koin`, then push and open the PR.

---

## Part C: core modules → KMP (`chore/kmp-core`)

Each task converts modules to `whitelabel.kmp.library` (+ `whitelabel.koin`), moves `main` →
`commonMain` (platform code → `androidMain`) and tests → `commonTest`/`jvmTest`/`androidDeviceTest`,
and ends with `./gradlew <modules>:allTests assembleDebug` green.

### Task C1: `:core:branding`, `:core:analytics`, `:core:domain`

**Files:**
- Modify: the three `build.gradle.kts` files; move their sources and tests.

**Interfaces:** None new. Packages and public API are unchanged.

- [ ] **Step 1:** Move the tests to `commonTest` (`kotlin.test`). Run
  `./gradlew :core:branding:allTests :core:analytics:allTests :core:domain:allTests`. Expected:
  FAIL (still JVM modules: `allTests` missing).
- [ ] **Step 2:** Convert the build files and move the sources.
- [ ] **Step 3:** Run the same command, then `assembleDebug`. Expected: PASS on JVM and iOS.
- [ ] **Step 4:** Commit `refactor(kmp): move branding, analytics and domain to kmp`.

### Task C2: `:core:network`

**Files:**
- Modify: `core/network/build.gradle.kts`; move the sources.
- Create: `core/network/src/commonMain/kotlin/com/whitelabel/core/network/Platform.kt` (`expect`s)
- Create: `src/androidMain/.../Platform.android.kt`, `src/jvmMain/.../Platform.jvm.kt`,
  `src/iosMain/.../Platform.ios.kt`
- Modify: `SafeCall.kt` and `SessionAuth.kt` (use `kotlinx.io.IOException` and `isConnectivityFailure()`)
- Modify: `core/shell/.../brand/ShellKoin.kt` (`OkHttp.create()` → `defaultHttpEngine()`)
- Test: `core/network/src/commonTest/...` (moved). Tests using `:core:testing` go to `jvmTest`.
  Add `src/iosTest/.../ConnectivityFailureIosTest.kt`.

**Interfaces:**
- Produces:
  - `expect fun defaultHttpEngine(): HttpClientEngine`: OkHttp on Android/JVM, Darwin on iOS.
  - `internal expect fun Throwable.isConnectivityFailure(): Boolean`:
    - Android/JVM: `this is java.io.IOException || this is java.nio.channels.UnresolvedAddressException`;
    - iOS: `this is kotlinx.io.IOException || this is io.ktor.client.engine.darwin.DarwinHttpRequestException`.
  - `ModuleContributor` and `networkModule` move to `commonMain` unchanged.

- [ ] **Step 1: Write the failing test** `ConnectivityFailureIosTest`:
  `DarwinHttpRequestException(NSError(NSURLErrorDomain, NSURLErrorNotConnectedToInternet, null))`
  `.isConnectivityFailure()` is true, and `IllegalStateException().isConnectivityFailure()` is false.
  Add `SafeCallTest.ioExceptionIsNoInternet` to `commonTest`: a `MockEngine` that throws
  `kotlinx.io.IOException` maps to `DataError.Network.NO_INTERNET`.
- [ ] **Step 2: Run** `./gradlew :core:network:allTests`. Expected: FAIL.
- [ ] **Step 3: Implement**, moving the existing tests as described.
- [ ] **Step 4: Run** `./gradlew :core:network:allTests assembleDebug`. Expected: PASS.
- [ ] **Step 5: Commit** `refactor(kmp): move core:network to kmp with a darwin engine`.

### Task C3: `:core:datastore` on DataStore-okio

**Files:**
- Modify: `core/datastore/build.gradle.kts` (`datastore-core-okio`, `okio`; `commonTest`
  `okio-fakefilesystem`)
- Modify: `JsonStore.kt`, `TokenStore.kt`, `TokenCipher.kt`, `StorePaths.kt` → `commonMain`
- Move: `KeystoreTokenCipher.kt` → `androidMain`
- Create: `src/androidMain/.../StorePaths.android.kt`, `src/iosMain/.../StorePaths.ios.kt`,
  `src/commonMain/.../FileSystems.kt` (+ `actual`s)
- Test: `src/commonTest/...` (moved `JsonStore`/`TokenStore` tests, on `FakeFileSystem`),
  `src/androidDeviceTest/...` (moved Keystore test, plus `LegacyLocationTest`)

**Interfaces:**
- Produces:
  - `class JsonStore<T>(path: okio.Path, serializer: KSerializer<T>, default: T, scope: CoroutineScope, fileSystem: FileSystem = SystemFileSystem)`.
    Same behavior: a corrupt file reads as default, unknown keys are ignored.
  - `class TokenStore(path: okio.Path, cipher: TokenCipher, scope: CoroutineScope, fileSystem: FileSystem = SystemFileSystem)`.
    Base64 uses `kotlin.io.encoding.Base64.Default` (same alphabet and padding as
    `java.util.Base64.getEncoder()`).
  - `internal expect val SystemFileSystem: FileSystem`.
  - `fun interface StorePaths { fun path(name: String): okio.Path }`.
  - `fun Context.jsonStorePath(name: String): okio.Path`, the same file as today's
    `filesDir/datastore/<name>.json`. `jsonStoreFile` is deleted.
  - `fun iosJsonStorePath(name: String): okio.Path`:
    `<NSApplicationSupportDirectory>/datastore/<name>.json`, creating the directory.

- [ ] **Step 1: Write the failing tests.**
  - `commonTest`:
    - `JsonStoreTest.corruptFileReadsAsDefault`;
    - `unknownKeysAreIgnored`;
    - `updatesPersistAcrossInstances` (on `FakeFileSystem`);
    - `TokenStoreTest.base64MatchesTheJavaEncoding`: the sealed document for a fixed IV and
      ciphertext equals the string the old encoder produced, `"AAECAw=="` for bytes `0,1,2,3`;
    - the rest of the existing tests, moved.
  - `androidDeviceTest`: `LegacyLocationTest.readsADocumentAtTheOldPath`. Write
    `{"theme":"DARK"}` to `filesDir/datastore/preferences.json` directly, open a `JsonStore` at
    `context.jsonStorePath("preferences")`, and read `DARK`.
- [ ] **Step 2: Run** `./gradlew :core:datastore:allTests`. Expected: FAIL.
- [ ] **Step 3: Implement.** Update `core:data`'s `androidDataModule` to use `jsonStorePath`.
- [ ] **Step 4: Run** `./gradlew :core:datastore:allTests :core:datastore:connectedAndroidDeviceTest assembleDebug`.
  Expected: PASS.
- [ ] **Step 5: Commit** `refactor(kmp): move core:datastore to kmp on datastore-okio`.

### Task C4: `:core:data` and `:core:mockapi`

**Files:**
- Modify: both `build.gradle.kts` files. `appcompat` moves to `androidMain`.
- Move: `AppCompatLanguageRepository.kt` and `androidDataModule` → `core/data/src/androidMain`.
  Everything else → `commonMain`.
- Modify: `core/mockapi` sources → `commonMain`:
  - `java.time.Instant` → `kotlin.time.Clock.System`/`Instant`;
  - `UUID` → `kotlin.uuid.Uuid.random()`;
  - `ConcurrentHashMap` → maps guarded by `kotlinx.atomicfu.locks.SynchronizedObject` + `synchronized`;
  - `java.io.IOException` → `kotlinx.io.IOException`.

  The `META-INF/services` file moves to `src/androidMain/resources`, and `MockApiContributor` to
  `androidMain`.
- Test: moved tests. Contract tests (`:core:testing`) → `jvmTest`; the rest → `commonTest`.
  `core/data/src/androidTest` → `androidDeviceTest`. Add
  `core/mockapi/src/commonTest/.../FakeBackendConcurrencyTest.kt` and
  `core/data/src/iosTest/.../DataKoinIosTest.kt`.

**Interfaces:**
- Produces: `dataModule` in `commonMain`. It requires `StorePaths`, `TokenCipher` and
  `LanguageRepository` from a platform module: `androidDataModule` on Android, the iOS starter's
  Swift-backed module on iOS.

- [ ] **Step 1: Write the failing tests.**
  - `FakeBackendConcurrencyTest.parallelSignInsAndRefreshesKeepEveryToken`: 50 concurrent
    `launch`es on `Dispatchers.Default`, each signing in alice and refreshing once. Assert 50
    distinct live sessions and no `INVALID_REFRESH_TOKEN`.
  - `DataKoinIosTest.dataGraphResolvesWithPlatformFakes`: `koinApplication` with `dataModule`,
    `networkModule` and a module of fakes (`StorePaths` on a temp dir, `FakeTokenCipher`,
    `FakeLanguageRepository`, `MockEngine`, `NetworkConfig`, `BrandConfig`). It resolves
    `SessionRepository`, `UserRepository`, `AppConfigRepository` and `UserPreferencesRepository`.
- [ ] **Step 2: Run** `./gradlew :core:data:allTests :core:mockapi:allTests`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `./gradlew :core:data:allTests :core:mockapi:allTests :core:data:connectedAndroidDeviceTest :apps:showcase:connectedDebugAndroidTest`.
  Expected: PASS. The E2E run proves `ServiceLoader` still finds the mock from `androidMain/resources`.
- [ ] **Step 5: Commit** `refactor(kmp): move core:data and core:mockapi to kmp`.

### Task C5: `:core:reminders:shared`

**Files:**
- Create: `core/reminders/shared/build.gradle.kts` (`whitelabel.kmp.library`, `whitelabel.koin`,
  `kotlinx-datetime`, depends on `:core:datastore`)
- Move to `core/reminders/shared/src/commonMain/kotlin/com/whitelabel/core/reminders/`:
  `Ports.kt`, `Reminder.kt`, `ReminderEngine.kt`, `StoredReminder.kt`, `Reminders.kt`, plus the
  `JsonStore`-backed `ReminderStore` implementation, wherever it lives in `RemindersRuntime.kt`.
  `java.time.LocalDateTime` → `kotlinx.datetime.LocalDateTime`; `Duration` → `kotlin.time.Duration`.
- Keep in `:core:reminders` (Android): the alarm scheduler, notification poster, receivers,
  permission, `ReminderConfig`, `RemindersRuntime` and `remindersModule`. Add
  `api(project(":core:reminders:shared"))`.
- Modify: `gradle/foundation.settings.gradle.kts` (add `:core:reminders:shared`)
- Test: move the engine and store tests to `core/reminders/shared/src/commonTest`. Add
  `StoredReminderCompatTest`.

**Interfaces:**
- Produces:
  - `Reminder(id: String, at: kotlinx.datetime.LocalDateTime, channel: String, title: String, text: String, deepLink: String?)`.
  - `AlarmScheduler.set(id: String, at: kotlinx.datetime.LocalDateTime)`.
  - `ReminderEngine`'s constructor takes `clock: () -> LocalDateTime` (and the time zone it
    needs) instead of `java.time`.
  - Android adapters convert with `toJavaLocalDateTime()` at the `AlarmManager` boundary only.

- [ ] **Step 1: Write the failing test** `StoredReminderCompatTest.readsJavaTimeStrings`. A
  `StoredReminder` with `at` = each of `"2026-11-11T09:00"`, `"2026-11-11T09:00:30"` and
  `"2026-11-11T09:00:30.123456789"` converts with `toReminderOrNull()` to the matching
  `LocalDateTime`, and `toStored()` of `LocalDateTime(2026, 11, 11, 9, 0)` writes
  `"2026-11-11T09:00"`.
- [ ] **Step 2: Run** `./gradlew :core:reminders:shared:allTests`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `./gradlew :core:reminders:shared:allTests :core:reminders:testDebugUnitTest :core:reminders:connectedDebugAndroidTest`.
  Expected: PASS.
- [ ] **Step 5: Commit** `refactor(kmp): share the reminder engine`.

### Task C6: Part C checkpoint and docs

**Files:**
- Modify: `docs/architecture.md`:
  - the module table, with each module's platforms;
  - the "platform seams" list (`StorePaths`, `TokenCipher`, `LanguageRepository`,
    `defaultHttpEngine`, `AlarmScheduler`/`NotificationPoster`) and who implements each on iOS (the
    iOS starter).
- Modify: `docs/testing.md` (`FakeFileSystem`; contract tests in `jvmTest`)

- [ ] **Step 1:** Write the docs.
- [ ] **Step 2:** Run the part checkpoint.
- [ ] **Step 3:** Commit `docs(kmp): document the core modules' platforms and seams`, then push
  and open the PR.

---

## Part D: feature logic → KMP (`chore/kmp-features`)

Each feature task follows one recipe, so the recipe is written once here.

**Recipe for `:feature:<name>`:**
1. Create `feature/<name>/shared/build.gradle.kts` with:
   - plugins `whitelabel.kmp.library`, `whitelabel.kmp.resources`, `whitelabel.koin`;
   - `commonMain` deps: `:core:domain`, `:core:network`, `:core:analytics`, `:core:branding`,
     `:core:presentation`, `ktor-client-core`, `lifecycle-viewmodel`, `lifecycle-viewmodel-savedstate`;
   - `jvmTest`: `:core:testing`, `ktor-client-mock`.

   Add `include` to `foundation.settings.gradle.kts`.
2. Move into `feature/<name>/shared/src/commonMain/kotlin/com/whitelabel/feature/<name>/`: `domain/`,
   `data/`, every `*ViewModel.kt` with its State/Action/Event/UI models, any pure helpers in
   `presentation/` (e.g. `AuthErrors.kt`), and `<Name>Koin.kt`.
   - Screens, `*Root`, rows, the DSL file and `R` stay in `feature/<name>`.
   - The options classes the ViewModels read move to shared; the DSL keeps the composable-typed
     parts. Example: `SettingsOptions.sections`/`aboutNote` (`@Composable`/`@StringRes`) stay on an
     Android-side `SettingsOptions`, which hands the shared part to the ViewModel.
3. Make moved ViewModels, State, Action, Event, UI models and options `public`.
4. Every `UiText.Resource(R.string.x)` in moved code becomes `UiText.Resource(Res.string.x)`. Move
   those strings (en + fr) from `feature/<name>/src/main/res` to
   `feature/<name>/shared/src/commonMain/composeResources`, with the same names. Screens switch
   from `core.ui.UiText` to `core.presentation.UiText`.
5. `feature/<name>/build.gradle.kts`: `api(project(":feature:<name>:shared"))`, plus
   `:core:presentation`.
6. Tests: the ViewModel/data tests in `feature/<name>/src/test` move to `shared/src/commonTest`.
   Contract tests (`:core:testing`) go to `jvmTest`. Robot/screen tests stay in
   `feature/<name>/src/androidTest`.
7. Run `./gradlew :feature:<name>:shared:allTests :feature:<name>:testDebugUnitTest :feature:<name>:connectedDebugAndroidTest`.
   Expected: PASS, with the same test count as before plus the new tests.

### Task D1: `:feature:home:shared` (the pilot, smallest)

**Files:** per the recipe (`HomeViewModel.kt`, `home_greeting`/`home_greeting_named` →
composeResources).

**Interfaces:**
- Produces: `public class HomeViewModel`, `public data class HomeState(val greeting: UiText, …)`.

- [ ] **Step 1: Write the failing test** `HomeViewModelTest.greetingUsesFirstName`. When the user's
  first name is `Alice`, the state's greeting is `Res.string.home_greeting_named` with
  `args == listOf("Alice")`; with an empty name it is `home_greeting`. Put it in `commonTest`, so it
  also runs on iOS.
- [ ] **Step 2: Run** `./gradlew :feature:home:shared:allTests`. Expected: FAIL (module missing).
- [ ] **Step 3:** Apply the recipe.
- [ ] **Step 4:** Run recipe step 7. Expected: PASS.
- [ ] **Step 5:** Commit `refactor(kmp): share the home view model`.

### Task D2: `:feature:catalog:shared`

**Files:** per the recipe. `CatalogOptions` moves to shared. Coil stays in the UI module.

- [ ] **Step 1: Write the failing test** `CatalogListViewModelTest.searchDebounces`, ported to
  `commonTest` with `StandardTestDispatcher` and `advanceTimeBy`. It asserts the same debounce
  window as today's test.
- [ ] **Step 2: Run** `./gradlew :feature:catalog:shared:allTests`. Expected: FAIL.
- [ ] **Steps 3–4:** Apply the recipe, then run recipe step 7. Expected: PASS.
- [ ] **Step 5:** Commit `refactor(kmp): share the catalog view models`.

### Task D3: `:feature:settings:shared`

**Files:** per the recipe. Also create `shared/src/commonMain/.../presentation/LanguageTag.kt`.

**Interfaces:**
- Produces: `internal fun primaryLanguage(tag: String?): String?`, replacing
  `Locale.forLanguageTag(it).language`.

- [ ] **Step 1: Write the failing test** `LanguageTagTest`:
  - `primaryLanguage("fr-CA") == "fr"`;
  - `primaryLanguage("zh-Hant-TW") == "zh"`;
  - `primaryLanguage("en_US") == "en"`;
  - `primaryLanguage("FR") == "fr"`;
  - `primaryLanguage("") == null`;
  - `primaryLanguage(null) == null`.
- [ ] **Step 2: Run** `./gradlew :feature:settings:shared:allTests`. Expected: FAIL.
- [ ] **Step 3:** Implement `primaryLanguage` (split on `-`/`_`, lowercase, empty → `null`) and
  apply the recipe. `SettingsSection` stays in the UI module.
- [ ] **Step 4:** Run recipe step 7. Expected: PASS.
- [ ] **Step 5:** Commit `refactor(kmp): share the settings view model`.

### Task D4: `:feature:profile:shared`

**Files:** per the recipe (`ProfileViewModel`, `ChangePasswordViewModel`, the `profile_*` message
strings).

- [ ] **Step 1: Write the failing test** `ChangePasswordViewModelTest.tooShortShowsMinimum`, moved to
  `commonTest`: `currentError`/`newError` is `Res.string.profile_error_password_short` with
  `args == listOf(PasswordPolicy.MIN)`.
- [ ] **Step 2: Run** `./gradlew :feature:profile:shared:allTests`. Expected: FAIL.
- [ ] **Steps 3–4:** Apply the recipe, then run recipe step 7. Expected: PASS.
- [ ] **Step 5:** Commit `refactor(kmp): share the profile view models`.

### Task D5: `:feature:authentication:shared`

**Files:** per the recipe (login, register, forgot and reset ViewModels, `AuthErrors.kt`, the
`auth_error_*` strings). Deep links and the gate stay in the UI module.

- [ ] **Step 1: Write the failing test** `ResetPasswordViewModelTest.tokenComesFromSavedState`,
  moved to `commonTest`. `SavedStateHandle(mapOf("token" to "t1"))` → submitting calls the data
  source with `t1`. Use the key the current reset route uses.
- [ ] **Step 2: Run** `./gradlew :feature:authentication:shared:allTests`. Expected: FAIL.
- [ ] **Steps 3–4:** Apply the recipe, then run recipe step 7. Expected: PASS.
- [ ] **Step 5:** Commit `refactor(kmp): share the authentication view models`.

### Task D6: Showcase's iOS framework, cleanup, checkpoint and docs

**Files:**
- Create: `apps/showcase/shared/build.gradle.kts`:
  - `whitelabel.kmp.library`;
  - `iosArm64`/`iosSimulatorArm64` `binaries.framework { baseName = "ShowcaseShared"; isStatic = true }`;
  - `export(...)` and `api` of `:core:domain`, `:core:presentation`, `:core:data`, `:core:network`,
    `:core:datastore`, and every `:feature:*:shared`.
- Create: `apps/showcase/shared/src/commonMain/kotlin/com/whitelabel/apps/showcase/shared/ShowcaseModules.kt`
- Create: `apps/showcase/shared/src/iosTest/kotlin/.../ShowcaseGraphIosTest.kt`
- Modify: `settings.gradle.kts` (`include(":apps:showcase:shared")`)
- Modify: any remaining `com.whitelabel.core.ui.UiText` users (e.g. in `:core:shell`'s
  diagnostics) → `com.whitelabel.core.presentation.UiText`.
- Delete: `core/ui/.../UiText.kt`, `DataErrorText.kt` and `core_ui_error_*` from `core/ui` res, now
  unused. `ObserveAsEvents` stays. Afterwards `grep -r "core.ui.UiText"` returns nothing.
- Modify: `docs/architecture.md`, `docs/adding-a-shared-feature.md` (the recipe above, as the way to
  add a shared feature: UI module + `:shared`), `docs/creating-a-standalone-app.md` (an app's own
  `:shared` modules and its iOS framework module)
- Modify: `docs/superpowers/specs/2026-11-11-ios-kmp-stack-design.md` §4 (mark steps 1–4 done, with
  PR numbers)

**Interfaces:**
- Produces: `fun showcaseSharedModules(platform: Module): List<Module>`. It returns `networkModule`,
  `dataModule`, every feature's `<name>Module` and `platform`. This is the list the iOS starter will
  hand to `startKoin`.

- [ ] **Step 1: Write the failing test** `ShowcaseGraphIosTest.everySharedViewModelResolves`.
  Start a `koinApplication` with `showcaseSharedModules(iosFakes)`, where `iosFakes` binds
  `StorePaths` (temp dir), `FakeTokenCipher`, `FakeLanguageRepository`, `MockEngine`,
  `NetworkConfig`, `BrandConfig` and `Analytics`. Resolve each shared ViewModel (catalog list with
  `parametersOf(CatalogOptions())`; ViewModels that need it with `SavedStateHandle()`) and assert
  non-null.
- [ ] **Step 2: Run** `./gradlew :apps:showcase:shared:iosSimulatorArm64Test`. Expected: FAIL.
- [ ] **Step 3: Implement** the module and the cleanup.
- [ ] **Step 4: Run** `./gradlew :apps:showcase:shared:iosSimulatorArm64Test :apps:showcase:shared:linkDebugFrameworkIosSimulatorArm64`.
  Expected: PASS; the framework links.
- [ ] **Step 5:** Write the docs, run the part checkpoint, commit
  `refactor(kmp): add showcase's ios framework and drop the android ui text`, then push and open
  the PR.
