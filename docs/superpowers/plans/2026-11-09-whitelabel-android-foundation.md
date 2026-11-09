# White-Label Android Foundation & Multi-Client Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A Gradle multi-module Android project that builds `clientA` and `clientB` as independent, branded APKs from one codebase, with a tested core (result types, networking, client config, design system). This covers brief Phases 2 and 3.

**Architecture:** Each client is a directory under `clients/` containing `client.properties` and `res/`. A build-logic plugin validates the directory and registers it as a `client` flavor in `:app`, the only module with flavors. `:app` maps `BuildConfig` to a typed `BrandConfig` (Hilt). `:core:designsystem` builds `AppTheme` from brand resources, which each client's `res/` overrides. `:core:network` is a Ktor client that unwraps the backend envelope into `Result<T, DataError>`.

**Tech Stack:** Kotlin 2.2.20, AGP 8.13.2, Gradle 8.13, Compose BOM 2025.10.01 / Material 3, Hilt 2.57.2 (KSP), Ktor 3.3.1 (OkHttp engine), kotlinx-serialization 1.9.0, JUnit 5 + AssertK + Turbine, Compose UI tests on emulator `Medium_Phone_API_36.1`.

**Spec:** `docs/superpowers/specs/2026-11-09-whitelabel-android-design.md` (architecture, approved before this plan runs) and the brief `docs/superpowers/specs/2026-11-09-whitelabel-android-brief.md`. Backend contract: `../backend-api/docs/superpowers/specs/2026-11-09-whitelabel-backend-design.md` §9.

**Out of scope here (later plans):** `:core:domain/data/datastore/ui/navigation`, all features, auth/tokens, mock backend, CI workflows (Phases 4–5).

## Global Constraints

- Versions exactly as design §9, all in `gradle/libs.versions.toml`; no version literals in any `build.gradle.kts`. If a pinned coordinate fails to resolve, use the nearest newer patch of the same minor and note it in the phase report.
- JDK 17 toolchain; `compileSdk = 36`, `targetSdk = 36`, `minSdk = 26`.
- Packages: `com.whitelabel.core.<module>`, `com.whitelabel.app`, build-logic `com.whitelabel.buildlogic`. `:app` namespace `com.whitelabel.app` (application IDs come from clients).
- No client name, slug, URL or color appears in any Kotlin file or Gradle script outside `clients/<id>/`.
- Flavor dimension name: `client`. Debug `applicationIdSuffix = ".debug"`. Environment: `debug` → `DEVELOPMENT`, `release` → `PRODUCTION`.
- Pure Kotlin modules (`:core:common`, `:core:branding`, `:core:network`) have no Android imports.
- Conventions from the installed `android-*` skills (Result/DataError, naming without `Impl`, MVI State/Action, fakes over mocks, JUnit5 + AssertK), with Hilt instead of Koin.
- User-visible strings in `strings.xml` with `values-fr` translations; no hardcoded UI text. Touch targets ≥ 48dp; every icon-only control has a `contentDescription`.
- Signing material (`*.jks`, `*.keystore`, `clients/*/signing.properties`) is never committed.
- Git: follow `CLAUDE.md` → "Git workflow" (the same rules as `backend-api`): Conventional Commits, no commits on `main`, `scripts/check.sh` green before every push, one PR per phase, the owner merges, never `--no-verify`.
- Emulator for instrumented steps: `$ANDROID_HOME/emulator/emulator -avd Medium_Phone_API_36.1 -no-window -no-audio &` then `adb wait-for-device`. `ANDROID_HOME=~/Library/Android/sdk`.

## Review Focus

1. **Base URL / path joining.** A base URL without a trailing `/`, or a call path with a leading `/`, must still hit `…/api/v1/<path>` and must not drop the version prefix. Pinned in Task 2 (normalization) and Task 3 (`leading slash path still resolves under versioned base`).
2. **A release build pointing at a dev server.** `api.release.baseUrl` set to `http://…`, `localhost`, `127.0.0.1` or `10.0.2.2` must fail the Gradle build with the key named. Pinned in Task 2.
3. **Clutter in `clients/`.** macOS `.DS_Store` files, hidden folders, invalid directory names (`client-c`, `main`, `testX`), and two clients sharing an application ID or deep-link scheme must give a clear error or be ignored, never a confusing AGP failure. Pinned in Task 2.
4. **Non-envelope error responses.** An HTML 502 from a proxy, an empty 401 body, or an unknown error code must map to a `DataError.Api` with a status-based code, without crashing or reporting `SERIALIZATION`. Pinned in Task 3.
5. **Cancellation.** Leaving a screen mid-request must cancel the request. `CancellationException` must not be swallowed as `NO_INTERNET`/`UNKNOWN`. Pinned in Task 3 (`cancellation propagates`).

## Phases and PRs

| Phase | Branch (from `main`) | Tasks | PR opened at |
|---|---|---|---|
| 0 Git workflow | `feat/foundation` (first commit) | Task 0 (done) | with Phase 2 |
| 2 Foundation | `feat/foundation` | 1–6 | Task 6 checkpoint |
| 3 Multi-client | `feat/multi-client`, created from `main` after the Phase 2 PR is merged | 7–9 | Task 9 checkpoint |

Task 0 (done): `CLAUDE.md` git workflow, `.githooks/{commit-msg,pre-commit,pre-push}`,
`scripts/{check.sh,check-commit-messages.sh,github-protect.sh}`, `.github/workflows/ci.yml`
(job `ci`: JDK 17 → `scripts/check.sh` → commit-message check on PRs),
`.github/pull_request_template.md`, `.gitignore`. Later phases extend that workflow and do not
replace it: Phase 3 adds variant assembly + `verify-variants.sh`; Phase 5 adds the emulator job.

---

## Phase 2 — Foundation (branch `feat/foundation`)

### Task 1: Repository, Gradle skeleton, build-logic, `:core:common`

**Files:**
- Create: `gradle.properties`, `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `gradlew`, `gradlew.bat`, `gradle/wrapper/*`
- Create: `build-logic/settings.gradle.kts`, `build-logic/convention/build.gradle.kts`, `build-logic/convention/src/main/kotlin/` plugins `JvmLibraryConventionPlugin.kt` (`whitelabel.jvm.library`), `AndroidLibraryConventionPlugin.kt` (`whitelabel.android.library`), `AndroidLibraryComposeConventionPlugin.kt` (`whitelabel.android.library.compose`), `HiltConventionPlugin.kt` (`whitelabel.hilt`), shared `KotlinAndroid.kt`
- Create: `core/common/build.gradle.kts`, `core/common/src/main/kotlin/com/whitelabel/core/common/{Error.kt,Result.kt,DataError.kt,ApiErrorCode.kt}`
- Test: `core/common/src/test/kotlin/com/whitelabel/core/common/{ResultTest.kt,ApiErrorCodeTest.kt}`

**Interfaces:**
- Produces (`com.whitelabel.core.common`):
  - `interface Error`
  - `sealed interface Result<out D, out E : Error> { data class Success<out D>(val data: D); data class Error<out E : com.whitelabel.core.common.Error>(val error: E) }`, `typealias EmptyResult<E> = Result<Unit, E>`
  - `inline fun <T, E : Error, R> Result<T, E>.map(transform: (T) -> R): Result<R, E>`, `onSuccess(action: (T) -> Unit): Result<T, E>`, `onFailure(action: (E) -> Unit): Result<T, E>`, `fun <T, E : Error> Result<T, E>.asEmptyResult(): EmptyResult<E>`
  - `sealed interface DataError : Error { enum class Network : DataError { NO_INTERNET, REQUEST_TIMEOUT, SERIALIZATION, UNKNOWN }; data class Api(val code: ApiErrorCode, val httpStatus: Int, val fieldErrors: List<FieldError> = emptyList(), val retryAfterSeconds: Long? = null) : DataError; enum class Local : DataError { DISK_FULL, NOT_FOUND, UNKNOWN } }`
  - `data class FieldError(val path: String, val message: String)`
  - `enum class ApiErrorCode { VALIDATION_ERROR, INVALID_CLIENT, UNAUTHENTICATED, TOKEN_EXPIRED, INVALID_CREDENTIALS, INVALID_REFRESH_TOKEN, INVALID_RESET_TOKEN, FORBIDDEN, TENANT_SUSPENDED, TENANT_MISMATCH, NOT_FOUND, FEATURE_DISABLED, EMAIL_ALREADY_REGISTERED, PAYLOAD_TOO_LARGE, RATE_LIMITED, INTERNAL_ERROR, UNKNOWN; companion object { fun fromWire(code: String?): ApiErrorCode; fun fromStatus(status: Int): ApiErrorCode } }`
  - Convention plugin IDs above; the plugins are consumed by every later module.

- [ ] **Step 1: Confirm the repo state.** `git branch --show-current` prints `feat/foundation`, and `git config core.hooksPath` prints `.githooks` (Task 0 already set up the repo, `.gitignore` and hooks).
- [ ] **Step 2: Gradle wrapper.** Run `~/.gradle/wrapper/dists/gradle-8.13-bin/*/gradle-8.13/bin/gradle wrapper --gradle-version 8.13 --distribution-type bin` in the repo root. Expected: `gradlew` and `gradle/wrapper/gradle-wrapper.properties` reference 8.13.
- [ ] **Step 3: Catalog and settings.** Put every design §9 version and library in `libs.versions.toml`, including `ktor-client-{core,okhttp,content-negotiation,logging,mock}`, `ktor-serialization-kotlinx-json`, `hilt-{android,compiler,core}`, `junit-jupiter`, `assertk`, `turbine`, `kotlinx-coroutines-test`, `androidx-test-{runner,ext-junit}`, `compose-ui-test-{junit4,manifest}`, and plugin aliases for android-application/library, kotlin-jvm/android/compose/serialization, ksp, hilt, android-junit5. `settings.gradle.kts`: `pluginManagement { includeBuild("build-logic") }`, `google()`/`mavenCentral()` under `FAIL_ON_PROJECT_REPOS`, `rootProject.name = "android-starter-kit"`, `include(":core:common")`. `gradle.properties`: `org.gradle.jvmargs=-Xmx4g`, `org.gradle.configuration-cache=true`, `android.nonTransitiveRClass=true`, `kotlin.code.style=official`.
- [ ] **Step 4: Convention plugins.** `whitelabel.jvm.library`: kotlin-jvm, `jvmToolchain(17)`, `useJUnitPlatform()`, test deps junit-jupiter, assertk, coroutines-test. `whitelabel.android.library`: com.android.library + kotlin-android, compile/min SDK per Global Constraints, Java 17, android-junit5 plugin, the same test deps, `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"`. `whitelabel.android.library.compose`: kotlin compose plugin, `buildFeatures.compose = true`, BOM + ui, ui-tooling-preview, material3, debug ui-tooling + ui-test-manifest, androidTest ui-test-junit4 + ext-junit. `whitelabel.hilt`: applies ksp; if `com.android.base` is present, apply the hilt plugin + `hilt-android`/`ksp(hilt-compiler)`, otherwise `hilt-core` + `ksp(hilt-compiler)`. The build-logic catalog is read with `versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }`.
- [ ] **Step 5: Write failing tests** for `:core:common` (applies `whitelabel.jvm.library`):

```kotlin
// ResultTest
@Test fun `map transforms success and passes error through`() {
    assertThat(Result.Success(2).map { it * 3 }).isEqualTo(Result.Success(6))
    val err: Result<Int, DataError> = Result.Error(DataError.Network.NO_INTERNET)
    assertThat(err.map { it * 3 }).isEqualTo(Result.Error(DataError.Network.NO_INTERNET))
}
@Test fun `onSuccess and onFailure run only on their branch and return the same instance`()
@Test fun `asEmptyResult keeps the error`()
// ApiErrorCodeTest
@Test fun `every backend code round-trips by exact name`() {
    ApiErrorCode.entries.filter { it != ApiErrorCode.UNKNOWN }
        .forEach { assertThat(ApiErrorCode.fromWire(it.name)).isEqualTo(it) }
}
@Test fun `unknown, null, blank and lowercase codes map to UNKNOWN`() // "NEW_CODE", null, "", "not_found"
@Test fun `fromStatus maps by status`() // 401→UNAUTHENTICATED, 403→FORBIDDEN, 404→NOT_FOUND, 413→PAYLOAD_TOO_LARGE, 429→RATE_LIMITED, 500 and 599→INTERNAL_ERROR, 499, 600, 400, 200→UNKNOWN
```

- [ ] **Step 6: Run** `./gradlew :core:common:test`. Expected: FAIL (unresolved references).
- [ ] **Step 7: Implement** the Interfaces above. `fromWire` uses an exact, case-sensitive `entries.firstOrNull { it.name == code && it != UNKNOWN }`.
- [ ] **Step 8: Run** `./gradlew :core:common:test`. Expected: PASS.
- [ ] **Step 9: Commit** `build: gradle skeleton, convention plugins and core common`.

### Task 2: Client configuration loader (build-logic)

**Files:**
- Create: `build-logic/convention/src/main/kotlin/com/whitelabel/buildlogic/clients/{ClientConfig.kt,ClientConfigLoader.kt,InvalidClientConfigException.kt}`
- Test: `build-logic/convention/src/test/kotlin/com/whitelabel/buildlogic/clients/ClientConfigLoaderTest.kt` (JUnit5 + AssertK, `@TempDir` fixtures)

**Interfaces:**
- Produces:
  - `data class ClientConfig(val flavorName: String, val slug: String, val applicationId: String, val appName: String, val versionCode: Int, val versionName: String, val deepLinkScheme: String, val apiBaseUrls: Map<String, String> /* "debug","release" → URL ending in "/" */, val supportEmail: String?, val privacyPolicyUrl: String, val termsUrl: String, val defaultFeatures: List<String>, val clientDir: File)` with `val resDir get() = clientDir.resolve("res")` and `val testDir get() = clientDir.resolve("test")`
  - `object ClientConfigLoader { fun load(clientsDir: File): List<ClientConfig> }`. Sorted by `flavorName`. Throws `InvalidClientConfigException` listing **every** problem across all clients.
  - `class InvalidClientConfigException(val problems: List<String>) : GradleException(...)`. The message is `"Invalid client configuration:\n- " + problems.joinToString("\n- ")`; each problem starts with `clients/<dir>/client.properties: <key>`, or with the directory for directory-level problems.
  - `fun environmentFor(buildType: String): String`. `"debug"` → `"DEVELOPMENT"`, `"release"` → `"PRODUCTION"`, anything else → `IllegalArgumentException`.
- Rules are exactly those in design §3 (keys, regexes, URL rules, uniqueness, directory naming). Directory entries that are files or start with `.` are skipped. A directory without `client.properties` is a problem. The required asset files are `res/mipmap-anydpi-v26/ic_launcher.xml`, `res/mipmap-anydpi-v26/ic_launcher_round.xml` and `res/drawable/brand_splash_icon.xml`.

- [ ] **Step 1: Write failing tests**, one fixture writer `writeClient(dir, name, overrides: Map<String, String?>)` (a `null` value removes the key; assets are written by default):

```kotlin
@Test fun `valid client parses every field`() // slug acme, appId com.whitelabel.acme, versionCode 1, urls, features
@Test fun `base url without trailing slash is normalized`() // "http://10.0.2.2:3000/api/v1" → ".../api/v1/"
@Test fun `release url must be https`()            // problem mentions "api.release.baseUrl"
@Test fun `release url must not be a local host`() // localhost, 127.0.0.1, 10.0.2.2 each rejected
@Test fun `debug url may be cleartext to emulator host`()
@Test fun `all missing keys are reported at once`() // remove app.name and client.slug → 2 problems
@Test fun `invalid slugs are rejected`()          // "Acme", "a", "-acme", "acme_1"
@Test fun `invalid directory names are rejected`() // "client-c", "ClientC", "main", "debug", "release", "testClient", "androidTestX"
@Test fun `dot entries and stray files are ignored`() // ".DS_Store" file, ".idea" dir → only valid clients returned
@Test fun `duplicate application ids are rejected naming both clients`()
@Test fun `duplicate deep link schemes are rejected`()
@Test fun `version code must be an integer in range`() // "abc", "0", "2100000001"
@Test fun `default features are trimmed and blanks dropped`() // "catalog, ,profile," → [catalog, profile]
@Test fun `missing launcher or splash assets are reported`()
@Test fun `empty clients directory is an error`()
@Test fun `environment follows build type`() // debug, release, "staging" throws
```

- [ ] **Step 2: Run** `./gradlew -p build-logic :convention:test`. Expected: FAIL.
- [ ] **Step 3: Implement** `ClientConfigLoader`. Parse with `java.util.Properties` (UTF-8 reader), validate URLs with `java.net.URI`, and collect problems into a list instead of throwing on the first.
- [ ] **Step 4: Run** `./gradlew -p build-logic :convention:test`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(build): validated client configuration loader`.

### Task 3: `:core:network`

**Files:**
- Create: `core/network/build.gradle.kts` (`whitelabel.jvm.library`, `whitelabel.hilt`, kotlin-serialization)
- Create: `core/network/src/main/kotlin/com/whitelabel/core/network/{NetworkConfig.kt,HttpClientFactory.kt,ApiEnvelope.kt,SafeCall.kt,HttpClientExt.kt,Page.kt,di/NetworkModule.kt}`
- Test: `core/network/src/test/kotlin/com/whitelabel/core/network/{HttpClientFactoryTest.kt,SafeCallTest.kt,PaginationTest.kt}` plus a `testClient(handler)` helper using `MockEngine`
- Modify: `settings.gradle.kts` (include `:core:network`)

**Interfaces:**
- Consumes: `Result`, `DataError`, `FieldError`, `ApiErrorCode` (Task 1).
- Produces (`com.whitelabel.core.network`):
  - `data class NetworkConfig(val baseUrl: String, val clientSlug: String, val userAgent: String, val logger: ((String) -> Unit)? = null)`
  - `const val CLIENT_ID_HEADER = "X-Client-Id"`
  - `object HttpClientFactory { fun create(engine: HttpClientEngine, config: NetworkConfig): HttpClient }`. Configures ContentNegotiation JSON (`ignoreUnknownKeys = true`, `explicitNulls = false`), `HttpTimeout` (connect 15 s, request 30 s), `defaultRequest` (base URL, `X-Client-Id`, `Accept: application/json`, `User-Agent`), `expectSuccess = false`, and `Logging` at `HEADERS` only when `logger != null`, with `sanitizeHeader { it == HttpHeaders.Authorization }`.
  - `suspend inline fun <reified T> HttpClient.getResult(path: String, query: Map<String, Any?> = emptyMap()): Result<T, DataError>`; `getPageResult<reified T>(path, query): Result<Page<T>, DataError>`; `postResult<reified B, reified T>(path: String, body: B): Result<T, DataError>`; `patchResult<reified B, reified T>(path, body)`; `deleteResult<reified T>(path)`. Null query values are skipped, and a leading `/` in `path` is stripped.
  - `data class Page<T>(val items: List<T>, val page: Int, val pageSize: Int, val totalItems: Int, val totalPages: Int)`
  - Envelope DTOs and the `safeCall`/`toResult` internals are `@PublishedApi internal`.
  - `NetworkModule` (`@InstallIn(SingletonComponent::class)`): `@Provides @Singleton fun httpClient(engine: HttpClientEngine, config: NetworkConfig): HttpClient`. `:app` binds `HttpClientEngine` and `NetworkConfig`.
- Error mapping table: design §6, exactly.

- [ ] **Step 1: Write failing tests** (base URL `https://api.test/api/v1/`, slug `acme`):

```kotlin
@Test fun `sends client id, accept and user agent headers`()
@Test fun `relative path resolves under versioned base`()            // "health" → /api/v1/health
@Test fun `leading slash path still resolves under versioned base`() // "/health" → /api/v1/health
@Test fun `null query values are omitted`()
@Test fun `success envelope unwraps data and ignores unknown fields`()
@Test fun `post serializes body as json`()
@Test fun `error envelope maps code, status and field errors`() {
    // 400 {"success":false,"error":{"code":"VALIDATION_ERROR","message":"…","details":[{"path":"email","message":"Invalid email"}]},"meta":{"requestId":"r1"}}
    assertThat(result).isEqualTo(Result.Error(DataError.Api(ApiErrorCode.VALIDATION_ERROR, 400, listOf(FieldError("email", "Invalid email")))))
}
@Test fun `unknown error code maps to UNKNOWN keeping status`()  // 409 "SOMETHING_NEW"
@Test fun `html 502 body maps to INTERNAL_ERROR`()                 // text/html "<html>Bad gateway</html>"
@Test fun `empty 401 body maps to UNAUTHENTICATED`()
@Test fun `rate limited parses retry-after seconds`()            // 429 RATE_LIMITED, Retry-After: 30 → 30L
@Test fun `malformed success body maps to SERIALIZATION`()        // 200 {"success":true,"data":{"status":42}} for a String field
@Test fun `io failure maps to NO_INTERNET`()                       // engine throws java.io.IOException / UnresolvedAddressException
@Test fun `timeout maps to REQUEST_TIMEOUT`()                      // engine throws io.ktor.client.network.sockets.SocketTimeoutException
@Test fun `cancellation propagates`() = runTest {
    // handler: awaitCancellation(); launch getResult in a child job, cancel it, assert job.isCancelled and no Result was produced
}
@Test fun `logging redacts authorization`() // request with "Authorization: Bearer s3cret"; collected log lines contain no "s3cret"
// PaginationTest
@Test fun `page result returns items and pagination meta`()
@Test fun `page past the end is an empty success`()               // data [] with pagination page 9, totalPages 3
```

- [ ] **Step 2: Run** `./gradlew :core:network:test`. Expected: FAIL.
- [ ] **Step 3: Implement.** `safeCall` catches in this order: `CancellationException` → rethrow; `HttpRequestTimeoutException`/`SocketTimeoutException` → `REQUEST_TIMEOUT`; `IOException`/`UnresolvedAddressException` → `NO_INTERNET`; `SerializationException`/`JsonConvertException` → `SERIALIZATION`; `Exception` → `Network.UNKNOWN`. Error bodies are read as text and decoded leniently, and any decode failure falls back to `fromStatus`.
- [ ] **Step 4: Run** `./gradlew :core:network:test`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(network): ktor client with envelope unwrapping and error mapping`.

### Task 4: `:core:designsystem`

**Files:**
- Create: `core/designsystem/build.gradle.kts` (`whitelabel.android.library`, `whitelabel.android.library.compose`)
- Create: `core/designsystem/src/main/res/values/brand.xml` (defaults: 21 roles × `brand_light_*`/`brand_dark_*` from the Material 3 baseline scheme; `brand_corner_small` 8dp, `_medium` 12dp, `_large` 16dp), `res/raw/brand_keep.xml` (`tools:keep="@font/brand_*"`), `res/values/strings.xml`, `res/values-fr/strings.xml`
- Create: `core/designsystem/src/main/kotlin/com/whitelabel/core/designsystem/theme/{AppTheme.kt,BrandColors.kt,BrandTypography.kt,BrandShapes.kt,Spacing.kt,ColorContrast.kt}`
- Create: `.../component/{AppButtons.kt,AppTextField.kt,AppCard.kt,AppConfirmDialog.kt,AppLoading.kt,ErrorState.kt,EmptyState.kt,AppScaffold.kt}`, `.../catalog/DesignSystemCatalog.kt`
- Test: `core/designsystem/src/test/kotlin/.../theme/ColorContrastTest.kt`, `core/designsystem/src/androidTest/kotlin/.../{ComponentsTest.kt,AppThemeTest.kt}`
- Modify: `settings.gradle.kts`

**Interfaces:**
- Produces (`com.whitelabel.core.designsystem`):
  - Color roles, which are the resource names (snake_case of the `ColorScheme` property): `primary, on_primary, primary_container, on_primary_container, secondary, on_secondary, secondary_container, on_secondary_container, tertiary, on_tertiary, tertiary_container, on_tertiary_container, error, on_error, background, on_background, surface, on_surface, surface_variant, on_surface_variant, outline`. Fonts are optional: `font/brand_display`, `font/brand_body`.
  - `@Composable fun AppTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit)`; `object AppTheme { val spacing: Spacing @Composable @ReadOnlyComposable get() }`
  - `@Composable fun brandColorScheme(darkTheme: Boolean): ColorScheme`. It starts from `lightColorScheme()`/`darkColorScheme()` and overrides the 21 roles from resources.
  - `data class Spacing(val xs: Dp = 4.dp, val sm: Dp = 8.dp, val md: Dp = 16.dp, val lg: Dp = 24.dp, val xl: Dp = 32.dp)`
  - `fun contrastRatio(foreground: Color, background: Color): Double`; `data class ContrastPair(val name: String, val foreground: Color, val background: Color)`; `fun ColorScheme.contrastPairs(): List<ContrastPair>` (the 10 `on*`-over-base pairs among the roles above)
  - Components: `AppPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false)`, `AppSecondaryButton(...)` and `AppTextButton(...)` with the same parameters minus `loading`; `AppTextField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, error: String? = null, isPassword: Boolean = false, keyboardOptions: KeyboardOptions = KeyboardOptions.Default, singleLine: Boolean = true)`; `AppCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit)`; `AppConfirmDialog(title: String, text: String, confirmLabel: String, dismissLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit)`; `AppLoadingIndicator(modifier)`, `FullScreenLoading(modifier)`; `ErrorState(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null)`; `EmptyState(title: String, message: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null)`; `AppScaffold(title: String, modifier: Modifier = Modifier, onNavigateBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}, bottomBar: @Composable () -> Unit = {}, snackbarHostState: SnackbarHostState? = null, content: @Composable (PaddingValues) -> Unit)`; `DesignSystemCatalog(modifier: Modifier = Modifier)`
  - Test tags: `"AppButtonProgress"`, `"FullScreenLoading"`. String resources: `ds_retry`, `ds_show_password`, `ds_hide_password`, `ds_navigate_back`.
- Fonts: `BrandTypography` resolves `brand_display`/`brand_body` once via `resources.getIdentifier(name, "font", context.packageName)`. It uses `FontFamily(Font(id))` when the font is found and `FontFamily.Default` otherwise. Display/headline/title styles use display; body/label styles use body.

- [ ] **Step 1: Write the failing JVM test** `ColorContrastTest`: black on white = 21.0 (±0.01); identical colors = 1.0; `contrastRatio(a, b) == contrastRatio(b, a)`; `#767676` on white ≥ 4.5 and `#777777` on white < 4.5.
- [ ] **Step 2: Run** `./gradlew :core:designsystem:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3: Implement** `ColorContrast.kt` with the WCAG formula `(L1 + 0.05) / (L2 + 0.05)`, using `Color.luminance()`.
- [ ] **Step 4: Run** the same command. Expected: PASS.
- [ ] **Step 5: Write failing instrumented tests** (`createAndroidComposeRule<ComponentActivity>()`):

```kotlin
@Test fun passwordField_hidesTextUntilToggled()  // assert SemanticsProperties.Password present; click "Show password"; Password absent
@Test fun textField_error_showsMessageAndErrorSemantics()
@Test fun primaryButton_loading_isDisabledAndShowsProgress() // assertIsNotEnabled + onNodeWithTag("AppButtonProgress").assertExists()
@Test fun primaryButton_atFontScale2_keepsFullTextAndMinHeight() // Density(fontScale = 2f); assertTextEquals; height ≥ 48.dp
@Test fun errorState_retryInvokesCallback_andIsAbsentWithoutCallback()
@Test fun emptyState_showsTitleMessageAndAction()
@Test fun confirmDialog_routesConfirmAndDismiss()
// AppThemeTest
@Test fun darkTheme_usesBrandDarkPrimary()  // capture MaterialTheme.colorScheme.primary == colorResource(R.color.brand_dark_primary)
@Test fun lightTheme_usesBrandLightPrimary()
@Test fun defaultBrandColors_meetContrastAA() // every brandColorScheme(dark).contrastPairs() ≥ 4.5 for both themes
```

- [ ] **Step 6: Run** with the emulator up: `./gradlew :core:designsystem:connectedDebugAndroidTest`. Expected: FAIL.
- [ ] **Step 7: Implement** the theme, tokens and components. Every component and the catalog get light and dark `@Preview`s. Dynamic color is not used.
- [ ] **Step 8: Run** `./gradlew :core:designsystem:connectedDebugAndroidTest :core:designsystem:testDebugUnitTest`. Expected: PASS.
- [ ] **Step 9: Commit** `feat(designsystem): brand-resource theme, tokens and components`.

### Task 5: `:core:branding` and `:app` running as clientA

**Files:**
- Create: `core/branding/build.gradle.kts` (`whitelabel.jvm.library`), `core/branding/src/main/kotlin/com/whitelabel/core/branding/BrandConfig.kt`
- Create: `build-logic/convention/src/main/kotlin/AndroidApplicationConventionPlugin.kt` (`whitelabel.android.application`)
- Create: `app/build.gradle.kts`, `app/proguard-rules.pro`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/themes.xml`, `app/src/main/res/xml/network_security_config.xml`, `app/src/debug/res/xml/network_security_config.xml`
- Create: `app/src/main/kotlin/com/whitelabel/app/{WhitelabelApp.kt,MainActivity.kt}`, `.../brand/{BrandConfigFactory.kt,BrandModule.kt}`, `.../diagnostics/{HealthDataSource.kt,KtorHealthDataSource.kt,BrandPreviewViewModel.kt,BrandPreviewScreen.kt}`
- Create: `clients/clientA/client.properties`, `clients/clientA/res/{mipmap-anydpi-v26/ic_launcher.xml,mipmap-anydpi-v26/ic_launcher_round.xml,drawable/ic_launcher_foreground.xml,drawable/ic_launcher_monochrome.xml,drawable/brand_splash_icon.xml,values/brand.xml}`, `clients/clientA/test/com/whitelabel/app/ClientABrandTest.kt`
- Test: `app/src/test/kotlin/com/whitelabel/app/{brand/BrandConfigFactoryTest.kt,diagnostics/KtorHealthDataSourceTest.kt,diagnostics/BrandPreviewViewModelTest.kt}`
- Modify: `settings.gradle.kts` (include `:core:branding`, `:app`)

**Interfaces:**
- Consumes: `ClientConfigLoader`, `environmentFor` (Task 2); `HttpClientFactory`, `NetworkConfig`, `getResult` (Task 3); `AppTheme`, `DesignSystemCatalog`, components (Task 4).
- Produces:
  - `com.whitelabel.core.branding`: `data class BrandConfig(val clientSlug: String, val appName: String, val applicationId: String, val versionName: String, val environment: Environment, val apiBaseUrl: String, val deepLinkScheme: String, val runtimeDefaults: RuntimeDefaults)`; `enum class Environment { DEVELOPMENT, PRODUCTION }`; `data class RuntimeDefaults(val supportEmail: String?, val privacyPolicyUrl: String, val termsUrl: String, val enabledFeatures: Set<String>)`
  - `whitelabel.android.application`: com.android.application + kotlin-android + `whitelabel.hilt`, plus the same Compose setup as `whitelabel.android.library.compose` (extract it into a shared `configureCompose()` in `KotlinAndroid.kt` so androidTest gets ui-test deps too). The `:app` dependencies are `:core:branding`, `:core:network`, `:core:designsystem`, ktor-client-okhttp, core-splashscreen, activity-compose, lifecycle-runtime-compose, hilt-navigation-compose; tests add ktor-client-mock and turbine. It sets `buildFeatures.buildConfig = true`, `flavorDimensions += "client"`, debug `applicationIdSuffix = ".debug"`, and release `isMinifyEnabled = true`, `isShrinkResources = true`. For each `ClientConfig` it does what design §4 lists: flavor fields, `res.srcDir`, the `test<Flavor>` source dir, and the `BuildConfig` fields `CLIENT_SLUG, APP_NAME, API_BASE_URL, ENVIRONMENT, DEEP_LINK_SCHEME, SUPPORT_EMAIL, PRIVACY_POLICY_URL, TERMS_URL, DEFAULT_FEATURES` (all `String`, `SUPPORT_EMAIL` `""` when absent), set in `androidComponents.onVariants` from the variant's flavor and build type.
  - `com.whitelabel.app.brand`: `fun brandConfigOf(clientSlug: String, appName: String, applicationId: String, versionName: String, environment: String, apiBaseUrl: String, deepLinkScheme: String, supportEmail: String, privacyPolicyUrl: String, termsUrl: String, defaultFeatures: String): BrandConfig` and `fun buildConfigBrand(): BrandConfig` (which passes the `BuildConfig` fields). `BrandModule` provides `@Singleton BrandConfig`, `NetworkConfig(baseUrl, clientSlug, userAgent = "$appName/$versionName (Android ${Build.VERSION.RELEASE})", logger = { Log.d("Http", it) }` only when not `PRODUCTION`), and `HttpClientEngine = OkHttp.create()`.
  - `com.whitelabel.app.diagnostics`: `interface HealthDataSource { suspend fun check(): Result<HealthStatus, DataError> }`, `data class HealthStatus(val status: String, val databaseUp: Boolean)`, `class KtorHealthDataSource @Inject constructor(private val httpClient: HttpClient) : HealthDataSource` (GET `health`, data `{status, db}`; `db == "up"`), `data class BrandPreviewState(val brand: BrandConfig, val darkTheme: Boolean? = null, val health: HealthUi = HealthUi.Idle)`, `sealed interface HealthUi { data object Idle; data object Checking; data class Up(val databaseUp: Boolean); data class Failed(val error: DataError) }`, `sealed interface BrandPreviewAction { data object OnPingClick; data class OnThemeSelect(val dark: Boolean?) }`, `@HiltViewModel class BrandPreviewViewModel @Inject constructor(brand: BrandConfig, health: HealthDataSource)` exposing `state: StateFlow<BrandPreviewState>` and `onAction(action)`.
  - `MainActivity` calls `installSplashScreen()` before `super.onCreate`, uses `enableEdgeToEdge()`, and wraps `BrandPreviewRoot` in `AppTheme(darkTheme = state.darkTheme ?: isSystemInDarkTheme())`. The screen shows app name, slug, environment, base URL, a Ping button with its result, a theme selector (system/light/dark) and `DesignSystemCatalog`. This screen is temporary: Phase 4 replaces it with the root NavHost and moves diagnostics to Settings › About in debug.
  - The manifest uses `@string/app_name`, `@mipmap/ic_launcher[_round]`, `@style/Theme.App.Starting` (parent `Theme.SplashScreen`; `windowSplashScreenBackground=@color/brand_splash_background`, `windowSplashScreenAnimatedIcon=@drawable/brand_splash_icon`, `postSplashScreenTheme=@style/Theme.App`) and `networkSecurityConfig`. The main config is the platform default. The debug overlay permits cleartext for `10.0.2.2` and `localhost` only.
- clientA values: design §3 and §8. The `brand.xml` primary is green (light `#0B6E4F`, dark `#7FD8B1`), with `brand_splash_background`, `ic_launcher_background` and matching `on*`/container roles. The icon is a circle glyph.

- [ ] **Step 1: Write failing tests:**

```kotlin
// BrandConfigFactoryTest
@Test fun `maps environment names`()                       // "DEVELOPMENT", "PRODUCTION"
@Test fun `unknown environment fails fast`()               // "STAGING" → IllegalStateException
@Test fun `features csv becomes a set`()                    // "catalog,profile" → {catalog, profile}; "" → {}
@Test fun `blank support email becomes null`()
// KtorHealthDataSourceTest (MockEngine + HttpClientFactory)
@Test fun `up database maps to databaseUp true`()           // {"success":true,"data":{"status":"ok","db":"up"},"meta":{"requestId":"x"}}
@Test fun `down database maps to databaseUp false`()        // db "down"
@Test fun `server error surfaces DataError`()
// BrandPreviewViewModelTest (FakeHealthDataSource, Dispatchers.setMain(UnconfinedTestDispatcher()), Turbine)
@Test fun `ping goes Checking then Up`()
@Test fun `ping failure goes Failed with the error`()
@Test fun `theme selection updates state`()                // null → true → false → null
// clients/clientA/test/.../ClientABrandTest
@Test fun `clientA build carries its own identity and api`() {
    val brand = buildConfigBrand()
    assertThat(brand.clientSlug).isEqualTo("acme")
    if (BuildConfig.DEBUG) {
        assertThat(brand.applicationId).isEqualTo("com.whitelabel.acme.debug")
        assertThat(brand.apiBaseUrl).isEqualTo("http://10.0.2.2:3000/api/v1/")
        assertThat(brand.environment).isEqualTo(Environment.DEVELOPMENT)
    } else {
        assertThat(brand.applicationId).isEqualTo("com.whitelabel.acme")
        assertThat(brand.apiBaseUrl).isEqualTo("https://api.platform.example.com/api/v1/")
        assertThat(brand.environment).isEqualTo(Environment.PRODUCTION)
    }
}
```

- [ ] **Step 2: Run** `./gradlew :app:testClientADebugUnitTest`. Expected: FAIL.
- [ ] **Step 3: Implement** `:core:branding`, the application plugin, `:app` and the clientA folder per the Interfaces.
- [ ] **Step 4: Run** `./gradlew :app:testClientADebugUnitTest :app:testClientAReleaseUnitTest :app:assembleClientADebug`. Expected: PASS, and an APK exists at `app/build/outputs/apk/clientA/debug/`.
- [ ] **Step 5: Smoke run.** `adb install -r` the APK, then `adb shell am start -n com.whitelabel.acme.debug/com.whitelabel.app.MainActivity`. Expected: the splash shows the clientA icon, then the preview screen shows "Acme", `acme` and `DEVELOPMENT`. If `../backend-api` is running (`npm run dev`), Ping shows Up. Otherwise Ping shows the no-internet error and the app does not crash. Take a screenshot (`adb exec-out screencap -p`) for the report.
- [ ] **Step 6: Commit** `feat(app): typed brand config and clientA flavor`.

### Task 6: Phase 2 checkpoint

**Files:**
- Create: `README.md` (what this is, prerequisites, `./gradlew :app:assembleClientADebug`, module list linking `docs/architecture.md`), `docs/architecture.md` (design §1, §2 with the diagram, §5 and §6, updated to what was actually built and marked "Phase 2")

- [ ] **Step 1: Full verification.** Run `./gradlew build -x lint` (all unit tests, all variants assemble), then `./gradlew :app:lintClientADebug :core:designsystem:lint`, then `./gradlew :core:designsystem:connectedDebugAndroidTest`. Expected: all green. Fix any lint errors; record warnings in the report.
- [ ] **Step 2: Write the docs** listed above.
- [ ] **Step 3: Commit** `docs: phase 2 architecture and readme`.
- [ ] **Step 4: Push and open the PR.** `git push -u origin feat/foundation` (the pre-push hook runs `scripts/check.sh`), then `gh pr create` filled in from the PR template. Expected: CI job `ci` is green.
- [ ] **Step 5: Report and wait.** Report what was built, the test counts, the screenshot, any version deviations, outstanding issues, and the PR link. Do not start Phase 3 until the owner has merged the PR.

---

## Phase 3 — Multi-client (branch `feat/multi-client`, from `main` after Phase 2 is merged)

### Task 7: clientB and per-variant brand verification

**Files:**
- Create: `clients/clientB/client.properties`, `clients/clientB/res/...` (the same required files as clientA, plus `font/brand_display.ttf`, an OFL font such as Archivo or Space Grotesk downloaded from the Google Fonts GitHub repo, with its `OFL.txt` committed beside it as `clients/clientB/fonts-LICENSE.txt`), `clients/clientB/test/com/whitelabel/app/ClientBBrandTest.kt`
- Create: `app/src/androidTest/kotlin/com/whitelabel/app/BrandContrastTest.kt`, `app/src/androidTest/kotlin/com/whitelabel/app/BrandResourcesTest.kt`

**Interfaces:**
- Consumes: `brandConfigOf`/`buildConfigBrand` (Task 5), `brandColorScheme`, `contrastPairs`, `contrastRatio` (Task 4).
- clientB values: slug `globex`, applicationId `com.whitelabel.globex`, name `Globex`, scheme `globex`, debug URL `http://10.0.2.2:3000/api/v1/`, release URL `https://api.globex.example.com/api/v1/`, features `catalog`, support `support@globex.example`, legal `https://globex.example/{privacy,terms}`. The primary is blue (light `#1F4FA3`, dark `#B1C5FF`), `brand_corner_*` are 0dp/2dp/4dp, and the icon is a triangle glyph.

- [ ] **Step 1: Write failing tests.** `ClientBBrandTest` mirrors `ClientABrandTest` with clientB values, and also asserts `runtimeDefaults.enabledFeatures == setOf("catalog")`. The instrumented tests (`AndroidJUnit4`, target context):

```kotlin
// BrandContrastTest: runs inside whichever variant is installed
@Test fun brandColors_meetWcagAA_inLightAndDark() // compose rule; for dark in [false,true] every brandColorScheme(dark).contrastPairs() ≥ 4.5, failure message names client, theme and pair
// BrandResourcesTest
@Test fun appName_matchesBuildConfig()            // context.getString(R.string.app_name) == BuildConfig.APP_NAME
@Test fun packageName_matchesApplicationId()       // context.packageName == BuildConfig.APPLICATION_ID
```

- [ ] **Step 2: Run** `./gradlew :app:testClientBDebugUnitTest`. Expected: FAIL (no clientB flavor).
- [ ] **Step 3: Add** the clientB folder. No Kotlin or Gradle file outside `clients/clientB/` changes. Check with `git status --porcelain` (only `clients/clientB/**` and the two new androidTest files).
- [ ] **Step 4: Run** `./gradlew :app:testClientADebugUnitTest :app:testClientBDebugUnitTest :app:testClientAReleaseUnitTest :app:testClientBReleaseUnitTest`, then with the emulator up `./gradlew :app:connectedClientADebugAndroidTest :app:connectedClientBDebugAndroidTest`. Expected: PASS. If contrast fails, fix the client's `brand.xml` colors, not the test.
- [ ] **Step 5: Commit** `feat(clients): add clientB (globex) with per-variant brand tests`.

### Task 8: Release builds, per-client signing, APK verification

**Files:**
- Modify: `build-logic/.../clients/ClientConfig.kt` (add `val signing: ClientSigning?`), `ClientConfigLoader.kt`, `ClientConfigLoaderTest.kt`, `AndroidApplicationConventionPlugin.kt`, `app/proguard-rules.pro`
- Create: `build-logic/.../clients/ClientSigning.kt`, `scripts/verify-variants.sh`, `clients/signing.properties.example`

**Interfaces:**
- Produces: `data class ClientSigning(val storeFile: File, val storePassword: String, val keyAlias: String, val keyPassword: String)`, parsed from `clients/<id>/signing.properties`, where `storeFile` is resolved relative to the client directory unless absolute. When the file is absent, `signing = null` and the plugin logs `"<flavor>: no signing.properties, release will be unsigned"` once. The plugin creates `signingConfigs.create(flavorName)` and assigns it to the flavor. The debug build type keeps the debug key.
- `scripts/verify-variants.sh [debug|release ...]` (default both). For every client directory it reads the expected values from `client.properties`, finds the variant APK, and checks with the newest `$ANDROID_HOME/build-tools/*/aapt2`: (1) `dump badging` package name = applicationId (+`.debug` for debug); (2) `application-label` = `app.name`; (3) `versionCode`/`versionName`; (4) no other client's applicationId appears in the APK's `classes*.dex` (`unzip -p … | grep -a -c`); (5) the `drawable/ic_launcher_foreground` file, located via `aapt2 dump resources`, has a different sha256 from every other client's in the same build type. It prints one `OK`/`FAIL` line per check per APK and exits non-zero if any check fails.

- [ ] **Step 1: Write failing loader tests:** `` `absent signing file gives null signing` ``, `` `incomplete signing file names the missing key` `` (no `keyAlias`), `` `signing store file must exist` ``, `` `relative store file resolves against client dir` ``.
- [ ] **Step 2: Run** `./gradlew -p build-logic :convention:test`. Expected: FAIL. **Implement**, then run again. Expected: PASS.
- [ ] **Step 3: Release builds.** `./gradlew :app:assembleClientARelease :app:assembleClientBRelease`. If R8 reports missing classes, copy the `-dontwarn` lines from `app/build/outputs/mapping/<variant>/missing_rules.txt` into `proguard-rules.pro`, then rebuild. Expected: two unsigned release APKs.
- [ ] **Step 4: Verify release launch.** Make a throwaway keystore in the session scratchpad (`keytool -genkeypair -keystore <scratch>/a.jks -alias a -keyalg RSA -validity 1 -storepass test123 -keypass test123 -dname CN=TestA`), write `clients/clientA/signing.properties` pointing at it, then build `assembleClientARelease`. `apksigner verify --print-certs` must show `CN=TestA`. Install it and launch `com.whitelabel.acme`; the preview screen must render, proving R8 kept serialization and Hilt. Confirm `git check-ignore clients/clientA/signing.properties` prints the path. Delete the file and the keystore.
- [ ] **Step 5: Write `verify-variants.sh`** and run `./gradlew assembleDebug assembleRelease && scripts/verify-variants.sh`. Expected: all `OK`, exit 0.
- [ ] **Step 6: Negative check.** Copy clientA's `ic_launcher_foreground.xml` over clientB's, run `./gradlew :app:assembleClientBDebug && scripts/verify-variants.sh debug`. Expected: a `FAIL` line on the icon check and exit ≠ 0. Restore with `git checkout clients/clientB`.
- [ ] **Step 7: Extend CI.** In `.github/workflows/ci.yml` job `ci`, after `scripts/check.sh`, add `./gradlew assembleDebug assembleRelease` and `scripts/verify-variants.sh`. Release builds stay unsigned in CI; secrets come in Phase 5.
- [ ] **Step 8: Commit** `feat(build): per-client release signing and apk verification`.

### Task 9: Client onboarding docs and Phase 3 checkpoint

**Files:**
- Create: `docs/adding-a-client.md`, `docs/branding.md`, `docs/release-signing.md`
- Modify: `README.md` (build commands for all four variants and `scripts/verify-variants.sh`), `docs/architecture.md` (flavor scaling, design §4, marked "Phase 3")

- [ ] **Step 1: `docs/adding-a-client.md`**, the brief's most important document. It covers: prerequisites (the tenant slug exists in the backend: `npm run tenant -- create`); copying `clients/clientA` to `clients/<name>`; the key-by-key `client.properties` table with validation rules (design §3); required vs optional assets and how to make adaptive icons (Android Studio Image Asset → export into `clients/<name>/res`); colors and contrast; the expectation test to copy; build/verify commands; and the "what you must not touch" list (no Kotlin/Gradle changes outside the client folder). It ends with a 10-line checklist.
- [ ] **Step 2: Dry-run the guide.** Follow it literally to create a throwaway `clients/clientC` (slug `initech`). Run `./gradlew :app:assembleClientCDebug :app:testClientCDebugUnitTest && scripts/verify-variants.sh debug`. Expected: PASS with no edits outside `clients/clientC`. Fix the guide wherever it was wrong or unclear, then `rm -rf clients/clientC`.
- [ ] **Step 3: `docs/branding.md`** (the resource contract: the 21 roles × light/dark, corners, fonts, splash, icons, contrast rule, previewing with the preview screen's theme switch) and **`docs/release-signing.md`** (`signing.properties` keys, keystore custody, upload keys vs Play App Signing, how CI will inject secrets in Phase 5, "never commit").
- [ ] **Step 4: Full verification.** Run `./gradlew build` (unit tests, lint, all four variants), then `scripts/verify-variants.sh`, then with the emulator up `./gradlew :core:designsystem:connectedDebugAndroidTest :app:connectedClientADebugAndroidTest :app:connectedClientBDebugAndroidTest`. Expected: all green.
- [ ] **Step 5: Commit** `docs: client onboarding, branding and signing guides`.
- [ ] **Step 6: Push and open the PR** (`git push -u origin feat/multi-client`, `gh pr create` from the template). Expected: CI `ci` is green.
- [ ] **Step 7: Report and wait.** Report the PR link, two screenshots (clientA, clientB, each in light and dark), the `verify-variants.sh` output, test counts, deviations and open issues. The next step is writing the Phases 4–5 plan against the backend's generated `openapi.json`.
