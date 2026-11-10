# Multi-App Foundation (Phase 3) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the single white-label `:app` into a foundation for many different apps. Each app is a thin module under `apps/<name>/` hosted by a shared `:core:shell`, proven by two apps (`showcase`, `globex`) built from the same shared modules.

**Architecture:** The application convention plugin reads the module's own `apps/<name>/app.properties` and writes the values as generated string resources (read by the shell) and as `BuildConfig` fields (read by the app's identity test). `:core:shell` owns the Application base, the launcher Activity, splash, theme, root navigation and bottom bar, and builds the UI from the app's `AppDefinition`, an explicit feature list built with a small DSL. App-specific features live in `apps/<name>/<feature>` modules.

**Tech Stack:** As in Phase 2: Kotlin 2.2.20, AGP 8.13.2, Gradle 8.13, Compose BOM 2025.10.01 / Material 3, Navigation Compose 2.9.5 (type-safe routes), Hilt 2.57.2, Ktor 3.3.1, kotlinx-serialization 1.9.0, DataStore 1.1.7. Tests use JUnit 5 + AssertK + Turbine; instrumented tests run on emulator `Medium_Phone_API_36.1`.

**Spec:** `docs/superpowers/specs/2026-11-10-reusable-app-foundation-design.md` (binding). It builds on `docs/superpowers/specs/2026-11-09-whitelabel-android-design.md`, where §5–§7 still hold.

**Out of scope:** auth and the gate, `toAuth()`, remote app config/flags, `:core:domain/data/ui/analytics`, shared features (Phase 4); mock backend, contract tests, emulator CI (Phase 5).

## Global Constraints

- Every version comes from `gradle/libs.versions.toml`. No version literals in build files. JDK 17; compile/target SDK 36, min SDK 26.
- **Dependency rules:**
  - Shared modules (`core/*`, `feature/*`) never depend on `apps/*`.
  - `apps/<x>/<feature>` modules never depend on another app.
  - Only app modules apply `whitelabel.android.application`.
- **No app identity in shared code:** no app name, application ID, tenant, URL or color outside `apps/<name>/`. Shared-module test fixtures may use sample values.
- One brand per app. No product flavors anywhere.
- Debug: `applicationIdSuffix = ".debug"` and `ENVIRONMENT = DEVELOPMENT`. Release: `PRODUCTION`, with minify and shrinkResources.
- **Namespaces:**
  - app modules: `com.whitelabel.apps.<name>`
  - app feature modules: `com.whitelabel.apps.<name>.<feature>`
  - shell: `com.whitelabel.core.shell`
  - datastore: `com.whitelabel.core.datastore`
- **Public API stays small:** a module exposes only what another module uses, and everything else is `internal`.
- **Plugin order:** in every `plugins {}` block, list `whitelabel.android.library`/`whitelabel.jvm.library` **before** `whitelabel.hilt`. The Hilt convention plugin picks the Android or JVM path at apply time.
- **Composition is explicit:** no reflection, no Hilt multibindings. An app's definition file is the complete list of what it contains.
- **UI rules:** user-visible strings live in `strings.xml` with a `values-fr` translation, touch targets are ≥ 48dp, and icon-only controls have a `contentDescription`.
- **Git** follows `CLAUDE.md`. Work on branch `feat/multi-app`, created from `main` **after PR #1 (`feat/foundation`) is merged**. Conventional Commits with the `Co-Authored-By` trailer. `scripts/check.sh` must be green before every push. One PR at the end; the owner merges.
- **Emulator:** `~/Library/Android/sdk/emulator/emulator -avd Medium_Phone_API_36.1 -no-window -no-audio -no-boot-anim &`, then `adb wait-for-device`.

## Review Focus

1. **A broken app definition** (no tab, a duplicate feature, or a start tab that isn't one of the tabs) must fail at app start with a message naming the problem, not as a blank screen. Pinned in Task 3.
2. **The start tab's flag is off, or every tab is off.** The app must fall back to the first visible tab, or show the empty state, never crash. Pinned in Task 3.
3. **Forgotten configuration.** The application plugin not applied, so the shell defaults are still in place, must fail with a message naming `app.properties`. An app that forgets its icon or splash must fail the build, not ship the shell's defaults. Pinned in Tasks 1 and 4.
4. **Two apps colliding.** Two apps with the same application ID or deep-link scheme must make `scripts/verify-apps.sh` fail, naming both apps. Pinned in Task 7.
5. **Corrupt local data.** A corrupt or older-format notes file must load as an empty list (or the default), not crash the app. Pinned in Task 6.

---

### Task 0: Branch and commit the design

- [ ] **Step 1:** After the owner has merged PR #1, run `git switch main && git pull && git switch -c feat/multi-app`.
- [ ] **Step 2:** Commit `docs/superpowers/specs/2026-11-10-reusable-app-foundation-design.md` and this plan as `docs: reusable app foundation design and phase 3 plan`. Add a line under the header of `docs/superpowers/plans/2026-11-09-whitelabel-android-foundation.md`: "Phase 3 of this plan is superseded by `2026-11-10-multi-app-foundation.md`".

### Task 1: Per-app config loader (build-logic)

**Files:**
- Create: `build-logic/convention/src/main/kotlin/com/whitelabel/buildlogic/apps/{AppConfig.kt,AppConfigLoader.kt,InvalidAppConfigException.kt,AppConfigFields.kt}`
- Move (git mv, package `…buildlogic.apps`): `clients/AndroidStringResource.kt` and `environmentFor` (from `ClientConfig.kt`)
- Test: `build-logic/convention/src/test/kotlin/com/whitelabel/buildlogic/apps/{AppConfigLoaderTest.kt,AppConfigFieldsTest.kt,AndroidStringResourceTest.kt}`
- Leave the old `clients/` package in place; Task 5 deletes it.

**Interfaces:**
- Produces (`com.whitelabel.buildlogic.apps`):
  - `data class AppConfig(val tenant: String?, val applicationId: String, val appName: String, val versionCode: Int, val versionName: String, val deepLinkScheme: String, val apiBaseUrls: Map<String, String> /* "debug","release" → URL ending "/" */, val supportEmail: String?, val privacyPolicyUrl: String, val termsUrl: String, val defaultFeatures: List<String>, val signing: AppSigning? /* always null until Task 7 */, val appDir: File)` with `val resDir get() = appDir.resolve("src/main/res")`
  - `object AppConfigLoader { fun load(appDir: File): AppConfig }`. It reads `appDir/app.properties` and throws `InvalidAppConfigException(problems: List<String>)` listing every problem. Each message starts with `apps/<dir>/app.properties: <key>`, or `apps/<dir>` for file-level problems.
  - `fun appConfigFields(config: AppConfig, buildType: String): Map<String, String>`, with exactly the keys `TENANT` (`""` when absent), `APP_NAME`, `API_BASE_URL`, `ENVIRONMENT`, `DEEP_LINK_SCHEME`, `SUPPORT_EMAIL` (`""` when absent), `PRIVACY_POLICY_URL`, `TERMS_URL` and `DEFAULT_FEATURES` (comma-joined).
  - `fun resourceNameFor(field: String): String = "foundation_" + field.lowercase()`
  - `androidStringResource(raw)` and `environmentFor(buildType)`, unchanged apart from the package.
- **Rules:** the design spec §3 table. Behaviour matches the old `ClientConfigLoader`, except:
  - `client.slug` becomes the **optional** `api.tenant`, still validated with `^[a-z0-9][a-z0-9-]{1,62}$` when present.
  - There are no flavor-name or cross-client rules.
  - Required assets and colors are checked under `src/main/res`.
  - A missing `app.properties` is reported as a single problem.

- [ ] **Step 1: Port `ClientConfigLoaderTest` to `AppConfigLoaderTest`.**
  - Fixtures write `<tmp>/app.properties` and `src/main/res/...`.
  - Keep every rule test from the old loader except the flavor-name, duplicate and directory-scan tests.
  - Add these tests:

```kotlin
@Test fun `tenant is optional`()                       // no api.tenant → tenant == null, no problems
@Test fun `tenant must match backend slug pattern when present`() // "Acme" → problem names api.tenant
@Test fun `missing app properties is one problem`()     // problems == ["apps/<dir>: missing app.properties"]
@Test fun `required assets and colors are checked under src main res`()
// AppConfigFieldsTest
@Test fun `fields cover exactly the documented keys`()  // keys == setOf("TENANT", …, "DEFAULT_FEATURES")
@Test fun `absent tenant and support email become empty strings`()
@Test fun `api url and environment follow the build type`() // debug → debug URL + DEVELOPMENT; release → release URL + PRODUCTION
@Test fun `resource names are prefixed and lowercased`()   // resourceNameFor("API_BASE_URL") == "foundation_api_base_url"
```

- [ ] **Step 2:** Run `./gradlew -p build-logic :convention:test`. Expected: FAIL (unresolved references).
- [ ] **Step 3:** Implement. Move the old loader's parsing and validation helpers into `AppConfigLoader`; do not rewrite them.
- [ ] **Step 4:** Run `./gradlew -p build-logic :convention:test`. Expected: PASS; the old `clients` tests still pass too.
- [ ] **Step 5:** Commit `feat(build): per-app config loader`.

### Task 2: Optional tenant in branding and network

**Files:**
- Modify: `core/branding/.../BrandConfig.kt`, `core/network/.../NetworkConfig.kt`, `core/network/.../HttpClientFactory.kt`, `app/src/main/kotlin/com/whitelabel/app/brand/{BrandConfigFactory.kt,BrandModule.kt}` (to keep `:app` compiling until Task 5), and the affected tests in `core/network` and `app/src/test` and `clients/clientA/test`
- Test: `core/network/src/test/.../HttpClientFactoryTest.kt`

**Interfaces:**
- Produces:
  - `BrandConfig.tenant: String?` replaces `clientSlug`; every other field is unchanged.
  - `NetworkConfig(baseUrl: String, tenant: String?, userAgent: String, logger: ((String) -> Unit)? = null)`.
  - `HttpClientFactory` sends `X-Client-Id` only when `tenant != null`.
  - The User-Agent is `"<applicationId>/<versionName> (Android <release>)"`. The shell builds it in Task 4; here, `BrandModule` uses it.

- [ ] **Step 1: Failing tests.**
  - `` `no tenant omits the client id header` `` (`assertThat(request.headers[CLIENT_ID_HEADER]).isNull()`).
  - Keep `` `sends client id, accept and user agent headers` `` with a tenant.
  - Update `BrandConfigFactoryTest` with `` `blank tenant becomes null` ``.
- [ ] **Step 2:** Run `./gradlew :core:network:test :app:testClientADebugUnitTest`. Expected: FAIL.
- [ ] **Step 3:** Implement and update the call sites.
- [ ] **Step 4:** Run `scripts/check.sh`. Expected: PASS.
- [ ] **Step 5:** Commit `refactor(network): make the tenant header optional`.

### Task 3: `:core:shell` composition API

**Files:**
- Create: `core/shell/build.gradle.kts` (`whitelabel.android.library`, `whitelabel.android.library.compose`, `whitelabel.hilt`, kotlin-serialization). Add `include(":core:shell")` to `settings.gradle.kts`.
- Create: `core/shell/src/main/kotlin/com/whitelabel/core/shell/{AppDefinition.kt,AppNavigator.kt,TabResolution.kt}`
- Test: `core/shell/src/test/kotlin/com/whitelabel/core/shell/{AppDefinitionTest.kt,TabResolutionTest.kt}` (JVM, JUnit 5)

**Interfaces:**
- Produces (`com.whitelabel.core.shell`):
  - `data class Tab(val route: Any /* a @Serializable route object */, @StringRes val label: Int, val icon: ImageVector)`
  - `class FeatureEntry(val id: String, val flagKey: String? = null, val tab: Tab? = null, val routes: NavGraphBuilder.(AppNavigator) -> Unit)`
  - `class AppDefinition internal constructor(val entries: List<FeatureEntry>, val startTabRoute: Any?)`. `val tabs: List<Tab>` holds the entries' tabs in declaration order.
  - `class AppDefinitionBuilder { fun add(entry: FeatureEntry); fun startTab(route: Any) }`
  - `fun appDefinition(block: AppDefinitionBuilder.() -> Unit): AppDefinition`. It throws `IllegalArgumentException` when there are no tabs (message contains "at least one tab"), on a duplicate entry id (message names the id), or when the start tab is not one of the tabs (message names the route).
  - `interface AppNavigator { fun toTab(route: Any); fun navigate(route: Any); fun back(); fun openUrl(url: String) }`. `toAuth()` arrives in Phase 4.
  - `fun AppDefinition.visibleTabs(remoteFlags: Map<String, Boolean>, defaultFeatures: Set<String>): List<Tab>`. An entry without `flagKey` is visible. One with a key uses `remoteFlags[key] ?: (key in defaultFeatures)`.
  - `fun AppDefinition.resolveStartTab(visible: List<Tab>): Tab?`. It returns the start tab if visible, else the first visible tab, else `null`.

- [ ] **Step 1: Failing tests**, using fake `@Serializable` route objects and `Icons.Default.*`:

```kotlin
@Test fun `definition keeps declaration order of tabs`()
@Test fun `no tab is rejected`()                 // assertFailure { appDefinition { add(entry("a", tab = null)) } }.messageContains("at least one tab")
@Test fun `duplicate entry id is rejected`()
@Test fun `start tab must be one of the tabs`()
@Test fun `start tab defaults to null meaning first`()
// TabResolutionTest
@Test fun `unflagged entries are always visible`()
@Test fun `remote flag wins over defaults`()      // remote {"catalog":false}, defaults {catalog} → hidden; remote {"catalog":true}, defaults {} → visible
@Test fun `missing remote key falls back to defaults then off`()
@Test fun `start tab falls back to first visible when its flag is off`()
@Test fun `no visible tab resolves to null`()
```

- [ ] **Step 2:** Run `./gradlew :core:shell:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run the same command. Expected: PASS.
- [ ] **Step 5:** Commit `feat(shell): app definition and tab resolution`.

### Task 4: Shell host (application, activity, navigation, brand from resources, diagnostics)

**Files:**
- Create in `core/shell/src/main/`:
  - `kotlin/com/whitelabel/core/shell/FoundationApplication.kt`
  - `kotlin/com/whitelabel/core/shell/FoundationActivity.kt`
  - `kotlin/com/whitelabel/core/shell/ShellNavHost.kt`
  - `kotlin/com/whitelabel/core/shell/brand/{BrandConfigFactory.kt,BrandResources.kt,ShellModule.kt}`
  - `kotlin/com/whitelabel/core/shell/diagnostics/*`: moved with `git mv` from `app/.../diagnostics`
  - `AndroidManifest.xml`
  - `res/values/{foundation_config.xml,themes.xml,strings.xml,brand_defaults.xml}`, `res/values-fr/strings.xml`
  - `res/mipmap-anydpi-v26/{ic_launcher,ic_launcher_round}.xml` and `res/drawable/{ic_launcher_foreground,brand_splash_icon}.xml` (neutral defaults)
  - `res/xml/{network_security_config,data_extraction_rules}.xml`
- Create in `core/shell/src/debug/`: `res/xml/network_security_config.xml` (moved from `app/src/debug`)
- Tests:
  - `core/shell/src/test/.../brand/BrandConfigFactoryTest.kt`, moved from `app`
  - `core/shell/src/test/.../diagnostics/*`, moved
  - `core/shell/src/androidTest/kotlin/.../{ShellNavHostTest.kt,BrandResourcesTest.kt}`
- Modify: `core/shell/build.gradle.kts`, which gains dependencies on `:core:branding`, `:core:network`, `:core:common`, `:core:designsystem`, navigation-compose, core-splashscreen, activity-compose, lifecycle-runtime-compose, hilt-navigation-compose, ktor-client-okhttp, kotlinx-serialization-json and `api(:core:branding)`.

**Interfaces:**
- Consumes: Task 3 API, Task 2 `BrandConfig`/`NetworkConfig`, and `HttpClientFactory`/`NetworkModule`, `AppTheme`, `ErrorState` and `EmptyState` from Phase 2.
- Produces:
  - `open class FoundationApplication : Application()`. An app subclasses it with `@HiltAndroidApp`.
  - `@AndroidEntryPoint class FoundationActivity : ComponentActivity`. It injects `AppDefinition` and `BrandConfig`, then: `installSplashScreen()` before `super.onCreate`, `enableEdgeToEdge()`, `AppTheme(isSystemInDarkTheme())`, then `ShellNavHost(definition, visibleTabs = definition.visibleTabs(emptyMap(), brand.runtimeDefaults.enabledFeatures))`. Phase 3 has no remote flags.
  - `@Composable fun ShellNavHost(definition: AppDefinition, visibleTabs: List<Tab>, modifier: Modifier = Modifier)`.
    - It registers every entry's `routes` and starts at `resolveStartTab(visibleTabs)`.
    - The bottom `NavigationBar` shows only when `visibleTabs.size >= 2`.
    - Tab clicks use the standard "single top + restore state + pop to start" pattern.
    - With no visible tab it shows `EmptyState` with the strings `shell_no_features_title` and `shell_no_features_message`.
  - **The manifest** declares the INTERNET permission. The `<application>` carries:
    - `icon=@mipmap/ic_launcher`, `roundIcon=@mipmap/ic_launcher_round`, `label=@string/app_name`
    - `theme=@style/Theme.Foundation`, `allowBackup=false`
    - `dataExtractionRules` and `networkSecurityConfig`

    `FoundationActivity` is exported with MAIN/LAUNCHER and `theme=@style/Theme.Foundation.Starting`. These are the old `:app` themes, renamed.
  - `res/values/foundation_config.xml` declares `foundation_<field>` strings for every Task 1 field, empty by default, plus `app_name`. App resValues override them.
  - `fun brandConfigOf(...)` is moved with `tenant` replacing `clientSlug`. `fun brandConfigFromResources(context: Context): BrandConfig` reads `R.string.foundation_*` and the package's `versionName`. If `foundation_api_base_url` is blank it throws `IllegalStateException`, with a message containing "app.properties" and "whitelabel.android.application".
  - `ShellModule` (`@InstallIn(SingletonComponent)`) provides `@Singleton BrandConfig` via `brandConfigFromResources`, `NetworkConfig` (User-Agent per Task 2; logger only outside PRODUCTION) and `HttpClientEngine = OkHttp.create()`. The diagnostics `@Binds` moves along with it.
  - `fun AppDefinitionBuilder.diagnostics()` adds `FeatureEntry(id = "diagnostics", tab = Tab(DiagnosticsRoute, R.string.shell_diagnostics_tab, Icons.Default.Info), routes = { composable<DiagnosticsRoute> { BrandPreviewRoot() } })`. `@Serializable data object DiagnosticsRoute`. The preview screen loses its theme selector (there's no Activity-level theme override any more); the ViewModel test for theme selection is deleted with it.

- [ ] **Step 1: Failing tests.**
  - The moved JVM tests are updated for the new package and `tenant`, plus `` `blank api url fails naming app properties` `` in `BrandConfigFactoryTest`. It tests a pure helper `requireConfigured(apiBaseUrl: String)` that `brandConfigFromResources` calls.
  - Instrumented tests (`createAndroidComposeRule<ComponentActivity>()`; test-only routes and tabs):

```kotlin
@Test fun oneVisibleTab_hidesBottomBar()
@Test fun twoVisibleTabs_showBottomBarAndSwitchOnClick()   // click second tab label → its screen text shown
@Test fun flaggedOffStartTab_startsAtFirstVisible()
@Test fun noVisibleTab_showsEmptyState()
// BrandResourcesTest: the shell's androidTest has its own res/values overriding foundation_* strings with test values
@Test fun brandConfigFromResources_readsGeneratedStrings()  // tenant null when foundation_tenant is "", environment DEVELOPMENT, enabledFeatures parsed
```

- [ ] **Step 2:** Run `./gradlew :core:shell:testDebugUnitTest :core:shell:connectedDebugAndroidTest` on the emulator. Expected: FAIL.
- [ ] **Step 3:** Implement. `:app` still compiles: do not delete it yet.
- [ ] **Step 4:** Run the same command. Expected: PASS.
- [ ] **Step 5:** Commit `feat(shell): host activity, navigation and brand from resources`.

### Task 5: Application plugin per app, and `apps/showcase`

**Files:**
- Modify: `build-logic/.../AndroidApplicationConventionPlugin.kt`. Remove flavors and the clients loading. Use `AppConfigLoader.load(projectDir)` and write `appConfigFields` per variant as BuildConfig fields and as resValues `resourceNameFor(key)`; for resValues, apply `androidStringResource` to every value. Set `app_name`, `applicationId`, `versionCode`/`versionName` and the `deepLinkScheme` placeholder.
- Create:
  - `apps/showcase/app.properties`
  - `apps/showcase/build.gradle.kts` (namespace `com.whitelabel.apps.showcase`, depends on `:core:shell`)
  - `apps/showcase/src/main/AndroidManifest.xml`, containing only `<application android:name=".ShowcaseApp"/>`
  - `apps/showcase/src/main/kotlin/com/whitelabel/apps/showcase/{ShowcaseApp.kt,ShowcaseDefinition.kt}`
  - `apps/showcase/src/test/kotlin/com/whitelabel/apps/showcase/ShowcaseIdentityTest.kt`
- Move: `clients/clientA/res/*` → `apps/showcase/src/main/res/` (git mv), and `app/proguard-rules.pro` + `app/lint.xml` → `apps/showcase/`.
- Delete:
  - `app/`
  - `clients/`
  - `build-logic/.../clients/` with its tests
- Modify: `settings.gradle.kts` (include `:apps:showcase`, drop `:app`); `scripts/check.sh` if it names `:app`.

**Interfaces:**
- Consumes: Task 1 (`AppConfigLoader`, `appConfigFields`, `resourceNameFor`) and Task 4 (`FoundationApplication`, `appDefinition`, `diagnostics()`).
- Showcase values:
  - The former clientA values with a new identity: `app.applicationId=com.whitelabel.showcase`, `app.name=Showcase` and `app.deepLinkScheme=showcase`.
  - `api.tenant=acme`, with the debug URL unchanged and `api.release.baseUrl=https://api.showcase.example/api/v1/`.
  - Legal URLs `https://showcase.example/{privacy,terms}`, support `support@showcase.example`, and `defaults.features=catalog,profile`.
  - Colors and icons are clientA's green set, unchanged.
- `ShowcaseDefinition`: `@Module @InstallIn(SingletonComponent::class) object ShowcaseDefinition { @Provides @Singleton fun definition(): AppDefinition = appDefinition { diagnostics() } }`.

- [ ] **Step 1: Failing test** `ShowcaseIdentityTest`. It asserts every `BuildConfig` field exactly for the current build type, as `ClientABrandTest` did:
  - debug: `APPLICATION_ID == "com.whitelabel.showcase.debug"`, `TENANT == "acme"`, the `http://10.0.2.2:3000/api/v1/` URL and `DEVELOPMENT`
  - release: the https URL and `PRODUCTION`
  - every remaining field as well
- [ ] **Step 2:** Run `./gradlew :apps:showcase:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3:** Implement the plugin change, the move and the deletions.
- [ ] **Step 4:** Run `./gradlew :apps:showcase:testDebugUnitTest :apps:showcase:testReleaseUnitTest :apps:showcase:assembleDebug :apps:showcase:assembleRelease && scripts/check.sh`. Expected: PASS.
- [ ] **Step 5: Smoke.**
  - Install the debug APK and launch with `adb shell monkey -p com.whitelabel.showcase.debug 1`.
  - Expect the green splash, then the diagnostics screen showing "Showcase", tenant `acme`, `DEVELOPMENT` and the 10.0.2.2 URL, with no bottom bar.
  - Ping works when `../backend-api` is running.
  - Save a screenshot to the SDD workspace.
- [ ] **Step 6:** Commit `refactor(app): move app into apps/showcase on the shared shell`.

### Task 6: `:core:datastore` (JSON store) and `apps/globex` with its notes feature

**Files:**
- Create:
  - `core/datastore/build.gradle.kts` (android library, kotlin-serialization, datastore-core). Add `androidx-datastore` (core artifact `androidx.datastore:datastore`, version `datastore`) to the catalog.
  - `core/datastore/src/main/kotlin/com/whitelabel/core/datastore/JsonStore.kt`
  - `core/datastore/src/test/.../JsonStoreTest.kt` (JVM, temp dir)
- Create `apps/globex/` with the same shape as showcase:
  - `app.properties`, `build.gradle.kts`, manifest
  - `GlobexApp.kt`, `GlobexDefinition.kt`
  - `src/main/res`: blue brand, corners 0/2/4dp, triangle icon
  - `src/test/.../GlobexIdentityTest.kt`
- Create `apps/globex/notes/`:
  - `build.gradle.kts` (android library + compose + hilt + serialization; depends on `:core:shell`, `:core:designsystem`, `:core:datastore`)
  - `src/main/kotlin/com/whitelabel/apps/globex/notes/{Notes.kt,NotesStore.kt,NotesViewModel.kt,NotesScreen.kt,NotesModule.kt}`
  - `res/values{,-fr}/strings.xml`
  - tests: `src/test/.../{NotesStoreTest.kt,NotesViewModelTest.kt}` and `src/androidTest/.../NotesScreenTest.kt`
- Modify: `settings.gradle.kts` (`:core:datastore`, `:apps:globex`, `:apps:globex:notes`).

**Interfaces:**
- Produces (`com.whitelabel.core.datastore`):
  - `class JsonStore<T>(private val file: File, private val serializer: KSerializer<T>, private val default: T, private val scope: CoroutineScope)` with `val data: Flow<T>` and `suspend fun update(transform: (T) -> T)`.
  - It is backed by `DataStoreFactory.create` with a JSON `Serializer<T>`.
  - A corrupt file is replaced by `default` (`ReplaceFileCorruptionHandler`), and unknown JSON keys are ignored.
  - `fun Context.jsonStoreFile(name: String): File` returns `filesDir/datastore/<name>.json`.
- Produces (`:apps:globex:notes`, public):
  - `fun AppDefinitionBuilder.notes()`, which adds `FeatureEntry(id = "notes", tab = Tab(NotesRoute, R.string.notes_tab, Icons.Default.Edit), routes = …)`.
  - Everything else is `internal`: `Note(id: String, text: String, createdAtEpochMillis: Long)`, the `NotesStore` wrapper over `JsonStore<List<Note>>`, and a VM with `NotesState(notes, draft)` and `NotesAction { OnDraftChange, OnAddClick, OnDeleteClick(id) }`.
  - A blank draft is not added. The id comes from `UUID.randomUUID()`, and the newest note is first.
- Globex values:
  - `app.applicationId=com.whitelabel.globex`, `app.name=Globex`, `app.deepLinkScheme=globex`, `api.tenant=globex`
  - the same debug URL, release `https://api.globex.example/api/v1/`
  - legal `https://globex.example/{privacy,terms}`, support `support@globex.example`, no `defaults.features`
  - primary light `#1F4FA3`, dark `#B1C5FF`
- `GlobexDefinition = appDefinition { notes(); diagnostics(); startTab(NotesRoute) }`. `NotesRoute` is public so the definition can name it.

- [ ] **Step 1: Failing tests.**

```kotlin
// JsonStoreTest (runTest + TestScope backgroundScope)
@Test fun `empty file yields default`()
@Test fun `update persists and survives a new store on the same file`()
@Test fun `corrupt file yields default instead of crashing`()   // write "{not json" first
@Test fun `unknown keys from a newer version are ignored`()
// NotesViewModelTest (fake NotesStore over an in-memory JsonStore in a temp file)
@Test fun `adding a note prepends it and clears the draft`()
@Test fun `blank or whitespace draft is not added`()
@Test fun `delete removes only that note`()
// GlobexIdentityTest — exact fields like ShowcaseIdentityTest
// NotesScreenTest (instrumented)
@Test fun addNote_showsItInList_andDeleteRemovesIt()
```

- [ ] **Step 2:** Run `./gradlew :core:datastore:testDebugUnitTest :apps:globex:notes:testDebugUnitTest :apps:globex:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3: Implement.** Create globex by following the steps you would put in `docs/creating-an-app.md`, and keep notes of them for Task 8.
- [ ] **Step 4:** Run the Step 2 command plus `:apps:globex:notes:connectedDebugAndroidTest`, `:apps:globex:assembleDebug` and `scripts/check.sh`. Expected: PASS.
- [ ] **Step 5: Smoke.**
  - Install both apps' debug builds side by side.
  - globex shows the blue splash and a bottom bar with Notes and Diagnostics. Adding a note survives an app restart (`adb shell am force-stop`, then relaunch).
  - showcase is unaffected.
  - Take a screenshot.
- [ ] **Step 6:** Check that `git diff --stat main...HEAD -- core/ feature/ build-logic/` shows no change made *by this task* outside `core/datastore` and the catalog. Commit `feat(globex): second app with an app-specific notes feature`.

### Task 7: Per-app signing, `scripts/verify-apps.sh`, CI

**Files:**
- Modify:
  - `build-logic/.../apps/{AppConfig.kt,AppConfigLoader.kt}` and `AppConfigLoaderTest.kt`, for signing
  - `AndroidApplicationConventionPlugin.kt`
  - `.github/workflows/ci.yml`
  - `.gitignore` (`apps/*/signing.properties`)
- Create:
  - `build-logic/.../apps/AppSigning.kt`
  - `scripts/verify-apps.sh`
  - `apps/signing.properties.example`

**Interfaces:**
- `data class AppSigning(val storeFile: File, val storePassword: String, val keyAlias: String, val keyPassword: String)`.
  - Parsed from `apps/<name>/signing.properties`.
  - `storeFile` is relative to the app dir unless absolute.
  - When the file is absent the value is `null`, and the plugin logs one line, "release will be unsigned". An incomplete file, or a store file that doesn't exist, is a problem.
  - The plugin creates `signingConfigs.create("release")` and sets it on the release build type when present.
- `scripts/verify-apps.sh [--config-only] [debug|release ...]`:
  - It treats every `apps/*/app.properties` as an app.
  - **Always:** application IDs and deep-link schemes must be unique across apps. A failure line names both apps.
  - **Unless `--config-only`:** for each built APK, use the newest `$ANDROID_HOME/build-tools/*/aapt2` and check:
    - package = applicationId (+`.debug`)
    - `application-label` = `app.name`
    - versionCode and versionName
    - no other app's applicationId appears in `classes*.dex`
    - the `drawable/ic_launcher_foreground` sha256 differs from every other app's in the same build type
  - It prints `OK`/`FAIL` per check and exits non-zero on any FAIL.
- **CI job `ci`:** after `scripts/check.sh`, run `./gradlew assembleDebug assembleRelease` then `scripts/verify-apps.sh`.

- [ ] **Step 1: Failing loader tests:** `` `absent signing file gives null` ``, `` `incomplete signing file names the missing key` ``, `` `signing store file must exist` `` and `` `relative store file resolves against app dir` ``.
- [ ] **Step 2:** Run `./gradlew -p build-logic :convention:test`. Expected: FAIL. Implement, run again, and expect PASS.
- [ ] **Step 3: Release with signing.**
  - Generate a throwaway keystore with `keytool` in the SDD workspace and write `apps/showcase/signing.properties`.
  - Run `./gradlew :apps:showcase:assembleRelease`. `apksigner verify --print-certs` must show the test CN.
  - Install and launch `com.whitelabel.showcase`: the diagnostics screen renders, which proves R8 kept the serialization and Hilt code.
  - `git check-ignore apps/showcase/signing.properties` must print the path.
  - Delete the signing file and the keystore.
- [ ] **Step 4:** Run `./gradlew assembleDebug assembleRelease && scripts/verify-apps.sh`. Expected: all OK, exit 0.
- [ ] **Step 5: Negative checks.**
  - (a) Set globex's `app.deepLinkScheme=showcase` and run `scripts/verify-apps.sh --config-only`. Expected: a FAIL naming both apps, exit ≠ 0.
  - (b) Copy showcase's `ic_launcher_foreground.xml` over globex's, rebuild globex debug and run `scripts/verify-apps.sh debug`. Expected: a FAIL on the icon check.
  - Restore with `git checkout apps/globex`.
- [ ] **Step 6:** Commit `feat(build): per-app signing, app verification script and ci`.

### Task 8: Documentation and checkpoint

**Files:**
- Create:
  - `docs/creating-an-app.md`
  - `docs/adding-a-shared-feature.md`
  - `docs/branding.md`
  - `docs/api-environments.md`
  - `docs/release-signing.md`
  - `docs/testing.md`
- Modify: `README.md`, `docs/architecture.md`, `CLAUDE.md` (project pointers: apps, shell, the "no app identity in shared code" rule)

- [ ] **Step 1: Write `docs/creating-an-app.md`**, the most important doc, using globex as the worked example. Cover:
  - the backend tenant prerequisite: `npm run tenant -- create` for shared mode, or none for a dedicated backend
  - the folder layout to create
  - the `app.properties` key table (spec §3)
  - icons, splash and colors, with a link to `branding.md`
  - the `Application` subclass
  - the definition file: picking shared features, `startTab`, and flags
  - adding an app-specific feature module
  - `settings.gradle.kts` includes
  - the identity test to copy
  - build, verify, sign and release commands
  - a "what you must not touch" list
  - a closing checklist of 10 lines or fewer
- [ ] **Step 2: Dry run.** Follow the guide literally to create a throwaway `apps/initech` with only `diagnostics()`. Run `./gradlew :apps:initech:assembleDebug :apps:initech:testDebugUnitTest && scripts/verify-apps.sh --config-only`. Expected: PASS with no edits outside `apps/initech` and `settings.gradle.kts`. Fix the guide wherever it was wrong, then remove `apps/initech` and its include.
- [ ] **Step 3: Write the other docs.**
  - **`adding-a-shared-feature.md`:** module layout, the DSL extension plus options as the only public API, `internal` by default, flag keys, tests, and the dependency rules.
  - **`branding.md`:** the resource contract from Phase 2, now under `src/main/res`.
  - **`api-environments.md`:** build-type URLs, tenant on/off, and the local backend.
  - **`release-signing.md`:** per-app `signing.properties`, and how CI will get secrets in Phase 5.
  - **`testing.md`:** which tests go where, identity tests, emulator commands.
  - **`architecture.md`:** foundation purpose, module table, the spec §2 diagram marking built vs Phase 4 modules, and the dependency rules.
  - **`README.md`:** quick start for both apps.
- [ ] **Step 4: Full verification.**
  - `scripts/check.sh`
  - `./gradlew assembleDebug assembleRelease && scripts/verify-apps.sh`
  - With the emulator: `./gradlew :core:designsystem:connectedDebugAndroidTest :core:shell:connectedDebugAndroidTest :apps:globex:notes:connectedDebugAndroidTest`

  All must be green.
- [ ] **Step 5:** Commit `docs: foundation guides for creating apps and shared features`.
- [ ] **Step 6:** Push and open the PR:
  - `git push -u origin feat/multi-app`, which runs the pre-push hook.
  - `gh pr create` from the template.
  - CI `ci` must be green.
- [ ] **Step 7:** Report and wait. Include the PR link, screenshots of both apps, the `verify-apps.sh` output, test counts and open issues. Next comes the Phase 4 plan (shared features).
