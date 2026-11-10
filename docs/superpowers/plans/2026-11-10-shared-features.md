# Shared Features (Phase 4) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the shared, ready-to-use features (auth with the whole-app gate, settings, profile, home and catalog) and the core modules under them. Showcase uses all of them; globex adds `auth()` and `settings()` next to its own notes feature.

**Architecture:** Session, user, app-config and preference state lives in `:core:domain` (interfaces) and `:core:data` (implementations), so features never read each other. `:core:network` gains a session plugin that refreshes on `TOKEN_EXPIRED` only. Encrypted tokens live in `:core:datastore`. The shell reads remote flags and the theme, holds the splash until the session is known, and, with `auth()`, switches between an auth graph and a main graph. Each feature is one module with one public DSL function, an options class and its root route.

**Tech Stack:** As in Phase 3 (Kotlin 2.2.20, AGP 8.13.2, Compose BOM 2025.10.01, Navigation 2.9.5, Hilt 2.57.2, Ktor 3.3.1, DataStore 1.1.7, JUnit 5 + AssertK + Turbine). New: `androidx.appcompat:appcompat` 1.7.1 (per-app language) and Coil 3.3.0 (`io.coil-kt.coil3:coil-compose`, `coil-network-okhttp`) for catalog images.

**Spec:** `docs/superpowers/specs/2026-11-10-reusable-app-foundation-design.md` (binding), §4, §5 and §7 Phase 4. It builds on `2026-11-09-whitelabel-android-design.md` §1, §6 and §7. The backend contract is `../backend-api/openapi/openapi.json` and `../backend-api/docs/contract.md`.

**Out of scope:** force update (`app.minSupportedVersionCode`), the sessions list (`GET auth/sessions`), push delivery, a catalog offline cache (and Room), guest mode, onboarding, backend runtime `branding`. Also Phase 5's mock backend, contract tests, E2E tests and the emulator CI job.

## Decisions this plan makes (owner: please confirm in review)

1. **Custom session plugin, not Ktor `Auth { bearer }`.** The backend returns 401 for `INVALID_CREDENTIALS` too (wrong login, and a wrong current password on password change). Ktor's bearer plugin refreshes on every 401. Our plugin follows the backend's "Android client guidance" (`../backend-api/docs/api.md`):
   - `TOKEN_EXPIRED` or `UNAUTHENTICATED` → one single-flight refresh, then one retry. A retry still rejected that way signs out.
   - A refresh with no response is retried once at once with the same refresh token (lost-response rule).
   - `INVALID_REFRESH_TOKEN` signs out.
   - Everything else passes through.
   - Token writes are compare-and-set on the pair the request used, so a late refresh never revives a signed-out session.

   (Changed after the Part A review: the first version signed out on `UNAUTHENTICATED` at once, against contract rule 4.)
2. **`:core:network` stays pure JVM** and doesn't depend on `:core:datastore` (dropping that edge from spec §2). It declares `SessionTokenStore`, and `:core:data` implements it on the encrypted `TokenStore`.
3. **`AppNavigator.toAuth()` is dropped.** With the whole-app gate, session state alone switches graphs, so nothing would call it. `AppNavigator` gains `canOpenTab(route)` for home's shortcuts.
4. **`FeatureEntry.routes` receives a `FeatureScope(navigator, deepLinkScheme)`** in place of the bare navigator, so features can declare deep links. Spec §4 says `FeatureEntry` has no per-feature session flag, and the gate stays whole-app. `requiresSignIn` is a build-time check only: such a feature without `auth()` fails at definition build (spec §4's "fail at definition-build time").
5. **Remote flags apply at cold start.** The splash waits up to 2 s for `GET app/config`, then the visible tabs are fixed for the process. A later change applies on the next launch, so tabs never vanish under the user.
6. **Theme and language are device settings**, applied instantly, offline and without auth. When signed in they are also sent to `PATCH settings`, best effort. Server settings are the source of truth for notifications only.
7. **Catalog:** no offline cache and no Paging 3 library, only manual next-page loading over `getPageResult`. Category chips come from `CatalogOptions.categories` because the backend has no categories endpoint.
8. **`diagnostics()` is removed from the DSL** (spec §4, Phase 4 content). The shell registers the diagnostics screen itself in DEVELOPMENT builds, and Settings › About links to it.
9. **`FoundationActivity` becomes an `AppCompatActivity`** with an AppCompat DayNight theme, which `AppCompatDelegate.setApplicationLocales` needs below API 33.

## Global Constraints

- Everything in the Phase 3 plan's Global Constraints still holds: versions only from `gradle/libs.versions.toml`, the dependency rules, no app identity in shared code, plugin order (library before `whitelabel.hilt`), no reflection or multibindings, strings in English and French, ≥ 48dp touch targets, and `contentDescription` on icon-only controls.
- **Namespaces:** `com.whitelabel.core.{ui,analytics,domain,data}`, `com.whitelabel.feature.<name>` for `feature/<name>` (`:feature:<name>`).
- **Module kinds:**
  - `:core:domain` and `:core:analytics` are `whitelabel.jvm.library`.
  - `:core:ui` is an Android library with Compose.
  - `:core:data` is an Android library with Hilt and serialization.
  - Features follow `docs/adding-a-shared-feature.md`.
- **Shared features never depend on each other.** A feature's public API is its DSL function, its options class and its root route (so apps can name it in `startTab` or home shortcuts). Everything else is `internal`.
- **Feature flag keys:** `catalog` (server-enforced, `FEATURE_DISABLED` when off) and `profile` (client-only). Home, settings and auth have no key.
- **Backend rules copied from the contract:**
  - Password length: 10–128 to register, reset or change; 1–128 to log in.
  - Names: 1–50, counted as `String.length` (UTF-16, the same as the backend's JS length).
  - Catalog: `q` ≤ 100, `category` 1–50, `pageSize` 1–100 (default 20), and `sort` ∈ `createdAt:asc|createdAt:desc|title:asc|title:desc` (default `createdAt:desc`).
  - A page past the end returns empty `data`.
- **Passwords are never trimmed, logged, put in `SavedStateHandle`, or sent to analytics.** Emails are trimmed. Analytics params never contain an email, a name or a token.
- `networkJson` uses `explicitNulls = false`, so a `null` property is **omitted** from request bodies. A body that must send `null` builds a `JsonObject`.
- **Git** follows `CLAUDE.md`. There is one branch and one PR per part, in order. Each branch is created from `main` after the previous part's PR is merged:

  | Part | Branch |
  |---|---|
  | A | `feat/core-session` |
  | B | `feat/auth` |
  | C | `feat/settings` |
  | D | `feat/profile` |
  | E | `feat/home` |
  | F | `feat/catalog` |

  Every part ends with the checkpoint below.
- **Part checkpoint:**
  1. `scripts/check.sh`.
  2. `./gradlew assembleDebug assembleRelease && scripts/verify-apps.sh`.
  3. The `connectedDebugAndroidTest` of every module the part touched, on the emulator (Phase 3 plan Global Constraints). Run them one module per Gradle invocation if UTP reports "device not found".
  4. A smoke run against the local backend (`../backend-api`: `docker compose up -d && npm run dev`).
  5. Docs updated.
  6. `git push -u origin <branch>` and `gh pr create` from the template.
  7. CI `ci` green.
  8. Report (PR link, screenshots, test counts), then wait for the owner's merge.

## Review Focus

1. **A 401 that isn't an expired token** (wrong password at login, or a wrong current password on change-password) must show "wrong password". It must not trigger a refresh or sign the user out. Pinned in Tasks 3 and 16.
2. **Refresh fails for lack of network** (or with a 5xx). The user stays signed in; the call fails with `NO_INTERNET` (or the refresh's server error). Only a rejected refresh, or a retry still `TOKEN_EXPIRED`/`UNAUTHENTICATED`, ends the session. Pinned in Task 3.
3. **Several calls expire at once** (home and the catalog loading together) must cause exactly one refresh. The backend revokes the session when a used refresh token is reused. Pinned in Task 3.
4. **A slow, unreachable or newer backend at start.** The app must leave the splash within about 2 s, using cached or default config. A config carrying an unknown theme value or unknown flag keys must still load. Pinned in Tasks 6 and 7.
5. **Impatient input:**
   - A double tap on submit sends one request.
   - A search typed quickly never shows an older query's results over a newer one.
   - Rotation or process death keeps the typed email but never the password.

   Pinned in Tasks 10 and 18.

---

## Part A: core session layer (`feat/core-session`)

### Task 0: Branch and commit the plan

- [ ] **Step 1:** `git switch main && git pull && git switch -c feat/core-session`.
- [ ] **Step 2:** Commit this plan as `docs: phase 4 shared features plan`.

### Task 1: `:core:ui`

**Files:**
- Create `core/ui/build.gradle.kts` (android library + compose; `implementation(:core:common)`, `api(libs.androidx.lifecycle.runtime.compose)`). Include `:core:ui` in `settings.gradle.kts`.
- Create in `core/ui/src/main/`: `kotlin/com/whitelabel/core/ui/{UiText.kt,DataErrorUiText.kt,ObserveAsEvents.kt}` and `res/values{,-fr}/strings.xml`.
- Test: `core/ui/src/test/kotlin/com/whitelabel/core/ui/DataErrorUiTextTest.kt`

**Interfaces:**
- Produces:
  - `sealed interface UiText`, with `data class Dynamic(val value: String)` and `data class Resource(@StringRes val id: Int, val args: List<Any> = emptyList())`, plus `@Composable fun asString(): String` and `fun asString(context: Context): String`.
  - `fun DataError.toUiText(): UiText`.
  - `@Composable fun <T> ObserveAsEvents(events: Flow<T>, key1: Any? = null, onEvent: (T) -> Unit)`. It collects on `Dispatchers.Main.immediate` while STARTED.

- [ ] **Step 1: Failing tests:**

```kotlin
@Test fun `no internet maps to the offline message`()            // Resource(R.string.core_ui_error_no_internet)
@Test fun `rate limited with retry after names the seconds`()    // Api(RATE_LIMITED, 429, retryAfterSeconds = 30) → Resource(core_ui_error_rate_limited_seconds, listOf(30L))
@Test fun `rate limited without retry after uses the plain message`()
@Test fun `session errors map to session expired`()              // UNAUTHENTICATED, TOKEN_EXPIRED, INVALID_REFRESH_TOKEN
@Test fun `every api code maps to a message`()                   // ApiErrorCode.entries.forEach { Api(it, 400).toUiText() is Resource }
```

  The rest of the mapping:
  - `REQUEST_TIMEOUT` → `core_ui_error_timeout`
  - `INTERNAL_ERROR` → `core_ui_error_server`
  - `TENANT_SUSPENDED` → `core_ui_error_suspended`
  - `FEATURE_DISABLED` → `core_ui_error_unavailable`
  - `NOT_FOUND` → `core_ui_error_not_found`
  - `Local.*` → `core_ui_error_storage`
  - anything else → `core_ui_error_unknown`

  Features map their own codes (e.g. `INVALID_CREDENTIALS`) before falling back to `toUiText()`.
- [ ] **Step 2:** `./gradlew :core:ui:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Same command. Expected: PASS.
- [ ] **Step 5:** Commit `feat(ui): ui text and error messages shared by features`.

### Task 2: `:core:analytics`

**Files:**
- Create `core/analytics/build.gradle.kts` (jvm library) and include it.
- Create `core/analytics/src/main/kotlin/com/whitelabel/core/analytics/{Analytics.kt,LoggingAnalytics.kt}`.
- Test: `core/analytics/src/test/kotlin/com/whitelabel/core/analytics/AnalyticsTest.kt`

**Interfaces:**
- Produces:
  - `data class AnalyticsEvent(val name: String, val params: Map<String, String> = emptyMap())`. Its `init` requires that `name` matches `^[a-z][a-z0-9_]{0,39}$`.
  - `interface Analytics { fun track(event: AnalyticsEvent) }`
  - `interface CrashReporter { fun record(throwable: Throwable) }`
  - `object AnalyticsEvents { SCREEN_VIEW = "screen_view"; LOGIN = "login"; SIGN_UP = "sign_up"; LOGOUT = "logout"; SEARCH = "search"; VIEW_ITEM = "view_item" }`
  - `class LoggingAnalytics(private val log: (String) -> Unit) : Analytics`, which logs `"<name> {k=v, …}"`, params sorted by key.
  - `class LoggingCrashReporter(private val log: (String, Throwable) -> Unit) : CrashReporter`

- [ ] **Step 1: Failing tests:** `` `event name must be lowercase snake case up to 40 chars` `` (`"Login"`, `"a".repeat(41)`, `"1x"` throw; `"view_item"` ok), and `` `logging analytics prints name and sorted params` ``.
- [ ] **Step 2:** `./gradlew :core:analytics:test`. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Same command. Expected: PASS.
- [ ] **Step 5:** Commit `feat(analytics): analytics and crash reporter interfaces`.

### Task 3: Session plugin in `:core:network`

**Files:**
- Create `core/network/src/main/kotlin/com/whitelabel/core/network/{SessionTokenStore.kt,SessionAuth.kt}`
- Modify `HttpClientFactory.kt` (new parameter) and `di/NetworkModule.kt` (inject `SessionTokenStore`).
- Test: `core/network/src/test/kotlin/com/whitelabel/core/network/SessionAuthTest.kt`; update `TestClient.kt`.

**Interfaces:**
- Produces:
  - `data class SessionTokens(val accessToken: String, val refreshToken: String)`
  - `interface SessionTokenStore { suspend fun current(): SessionTokens?; suspend fun replace(tokens: SessionTokens); suspend fun endSession() }`
  - `fun HttpRequestBuilder.publicRequest()`, which marks a request so it never carries the bearer and never refreshes.
  - `suspend inline fun <reified B, reified T> HttpClient.postPublicResult(path: String, body: B): Result<T, DataError>`, the same as `postResult` but public.
  - `HttpClientFactory.create(engine: HttpClientEngine, config: NetworkConfig, tokens: SessionTokenStore): HttpClient`
- Behaviour. `SessionAuth` is an `HttpSend` interceptor that does the following:
  1. If tokens exist and the request isn't public, add `Authorization: Bearer <access>`.
  2. On a 401 whose envelope code is `TOKEN_EXPIRED`, for a request that carried a bearer, take a `Mutex`:
     - If `current()` now holds a different access token, retry once with it.
     - Otherwise `POST auth/refresh` (public, body `{"refreshToken"}`, response `TokenPair`):
       - success → `replace`, then retry once;
       - a 401 → `endSession()`, and return the original response;
       - any other failure → rethrow the exception, or return the original response for a non-401, so the caller sees the real cause.
  3. On a 401 with `UNAUTHENTICATED` for a request that carried a bearer → `endSession()`.
  4. Any other response is returned untouched.

  Ktor 3 saves response bodies, so reading the 401 body here leaves it readable for `toApiError`.

- [ ] **Step 1: Failing tests** (MockEngine, a fake `SessionTokenStore` counting calls):

```kotlin
@Test fun `signed in request carries the bearer`()
@Test fun `signed out request has no authorization header`()
@Test fun `public request never carries the bearer`()
@Test fun `expired token refreshes once stores the pair and retries`()
@Test fun `concurrent expired calls share one refresh`()            // two async getResult calls; refresh endpoint hit exactly once; both Success
@Test fun `rejected refresh ends the session`()                    // refresh → 401 INVALID_REFRESH_TOKEN; endSession called; caller gets DataError.Api(TOKEN_EXPIRED, 401)
@Test fun `refresh without network keeps the session`()            // refresh throws IOException; caller gets NO_INTERNET; endSession not called; tokens unchanged
@Test fun `unauthenticated with a bearer ends the session`()
@Test fun `invalid credentials neither refreshes nor ends the session`()  // authenticated PATCH → 401 INVALID_CREDENTIALS; caller gets that code
@Test fun `a retried request that is expired again does not refresh twice`()
```
- [ ] **Step 2:** `./gradlew :core:network:test`. Expected: FAIL.
- [ ] **Step 3:** Implement. Existing tests build their client with a no-op token store.
- [ ] **Step 4:** Same command. Expected: PASS. (`:core:shell` won't compile until Task 5 binds the store; that's expected.)
- [ ] **Step 5:** Commit `feat(network): bearer session with single-flight refresh on expiry`.

### Task 4: Encrypted `TokenStore` in `:core:datastore`

**Files:**
- Create `core/datastore/src/main/kotlin/com/whitelabel/core/datastore/{TokenCipher.kt,KeystoreTokenCipher.kt,TokenStore.kt}`
- Tests: `core/datastore/src/test/.../TokenStoreTest.kt` and `core/datastore/src/androidTest/.../KeystoreTokenCipherTest.kt` (add the androidTest dependencies to the module).

**Interfaces:**
- Produces:
  - `class Sealed(val iv: ByteArray, val ciphertext: ByteArray)`
  - `interface TokenCipher { fun encrypt(plain: ByteArray): Sealed; fun decrypt(sealed: Sealed): ByteArray }`. Failures throw `GeneralSecurityException`.
  - `class KeystoreTokenCipher(alias: String = "foundation_session") : TokenCipher`. AES-256 `AES/GCM/NoPadding`, the key in AndroidKeyStore (created on first use), and a fresh IV per encryption.
  - `data class StoredTokens(val accessToken: String, val refreshToken: String)`
  - `class TokenStore(file: File, cipher: TokenCipher, scope: CoroutineScope) { val tokens: Flow<StoredTokens?>; suspend fun write(tokens: StoredTokens); suspend fun clear() }`. It is backed by a `JsonStore` of `{iv, ciphertext}` (Base64). A decryption failure clears the file and reads as `null`.

- [ ] **Step 1: Failing tests:**
  - JVM tests (`@TempDir`, a fake XOR cipher):
    - `` `written tokens read back` ``
    - `` `file never contains the plain tokens` ``
    - `` `undecryptable data reads as signed out and is cleared` ``
    - `` `clear reads as null` ``
  - Instrumented tests:
    - `roundTrip`
    - `twoEncryptions_useDifferentIvs`
- [ ] **Step 2:** `./gradlew :core:datastore:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Same command, plus `:core:datastore:connectedDebugAndroidTest`. Expected: PASS.
- [ ] **Step 5:** Commit `feat(datastore): keystore-encrypted token store`.

### Task 5: `:core:domain` and `:core:data`: session and user

**Files:**
- Create `core/domain` (jvm; `api(:core:common)`) with `kotlin/com/whitelabel/core/domain/{User.kt,Session.kt}`.
- Create `core/data` (android + hilt + serialization; `api(:core:domain)`, `implementation(:core:network, :core:datastore, ktor-client-core)`) with:
  - `kotlin/com/whitelabel/core/data/session/{DefaultSessionRepository.kt,StoreSessionTokens.kt,AuthDtos.kt}`
  - `kotlin/com/whitelabel/core/data/user/{DefaultUserRepository.kt,UserDto.kt,UserCache.kt}`
  - `kotlin/com/whitelabel/core/data/di/{DataModule.kt,ApplicationScope.kt}`
- Include both modules. `:core:shell` adds `implementation(:core:data)`.
- Tests: `core/data/src/test/.../session/DefaultSessionRepositoryTest.kt` and `.../user/DefaultUserRepositoryTest.kt` (MockEngine, `@TempDir`, the fake cipher).

**Interfaces:**
- Consumes: Task 3 `SessionTokenStore` and `postPublicResult`; Task 4 `TokenStore`.
- Produces:
  - `data class User(val id: String, val email: String, val firstName: String?, val lastName: String?)`
  - `sealed interface SessionState { data object Unknown; data object SignedOut; data object SignedIn }`
  - `data class Registration(val email: String, val password: String, val firstName: String?, val lastName: String?)`
  - `interface SessionRepository`:
    - `val state: StateFlow<SessionState>`
    - `suspend fun signIn(email: String, password: String): EmptyResult<DataError>`
    - `suspend fun register(registration: Registration): EmptyResult<DataError>`
    - `suspend fun signOut()`
  - `interface UserRepository`:
    - `val user: Flow<User?>`
    - `suspend fun refresh(): EmptyResult<DataError>`
    - `suspend fun update(firstName: String?, lastName: String?): EmptyResult<DataError>`

    (Task 15 implements `update`.)
  - `DataModule` binds `SessionRepository`, `UserRepository` and `SessionTokenStore` (`StoreSessionTokens`). It provides `TokenStore(context.jsonStoreFile("session"), KeystoreTokenCipher(), scope)` and `@ApplicationScope CoroutineScope` (`SupervisorJob() + Dispatchers.Default`).
- Behaviour:
  - `state` maps `tokenStore.tokens` to SignedIn/SignedOut, starting at `Unknown`.
  - `signIn`/`register` post `auth/login`/`auth/register` (public). The response is `{user, tokens}`. Write the user cache **before** the tokens, so SignedIn never shows without a user.
  - `signOut` reads the refresh token and clears local data first (tokens and user cache). It then posts `auth/logout` best effort, so offline sign-out works.
  - `StoreSessionTokens.endSession()` clears the same local data.

- [ ] **Step 1: Failing tests:**

```kotlin
@Test fun `state is unknown then signed out with no stored tokens`()
@Test fun `sign in stores user and tokens and flips to signed in`()
@Test fun `wrong password returns invalid credentials and stays signed out`()
@Test fun `register omits blank names from the body`()            // request body has no firstName key
@Test fun `sign out clears local data even when logout fails`()  // logout endpoint → IOException; state SignedOut; user null
@Test fun `ended session clears the cached user`()               // StoreSessionTokens.endSession()
// DefaultUserRepositoryTest
@Test fun `refresh caches the fetched user`()
@Test fun `cached user survives a new repository instance`()
```
- [ ] **Step 2:** `./gradlew :core:data:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** `./gradlew :core:data:testDebugUnitTest assembleDebug`. Expected: PASS, and the apps compile again.
- [ ] **Step 5:** Commit `feat(data): session and user repositories`.

### Task 6: App config and theme preference

**Files:**
- Create `core/domain/src/main/kotlin/com/whitelabel/core/domain/{AppConfig.kt,Preferences.kt}`
- Create `core/data/src/main/kotlin/com/whitelabel/core/data/config/{DefaultAppConfigRepository.kt,AppConfigDto.kt}` and `.../preferences/DefaultUserPreferencesRepository.kt`. Bind both in `DataModule`. `:core:data` adds `implementation(:core:branding)`.
- Tests: `core/data/src/test/.../config/DefaultAppConfigRepositoryTest.kt` and `.../preferences/DefaultUserPreferencesRepositoryTest.kt`

**Interfaces:**
- Produces:
  - `enum class ThemePreference { SYSTEM, LIGHT, DARK }`
  - `data class AppConfig(val features: Map<String, Boolean>, val supportEmail: String?, val privacyPolicyUrl: String, val termsUrl: String, val defaultTheme: ThemePreference, val supportedLanguages: List<String>)`
  - `interface AppConfigRepository { val config: StateFlow<AppConfig>; suspend fun refresh(): EmptyResult<DataError> }`
  - `interface UserPreferencesRepository { val theme: Flow<ThemePreference?>; suspend fun setTheme(theme: ThemePreference) }`. `null` means never chosen. It is stored in `JsonStore` `preferences`, which is device-wide and kept on sign-out.
- Behaviour:
  - Defaults come from `BrandConfig.runtimeDefaults`: empty `features`, its support/legal values, `SYSTEM`, and no language restriction.
  - `refresh()` calls `GET app/config` and caches the DTO in `JsonStore` `app_config`. The remote legal URLs and support email override the defaults.
  - `runtime.defaultTheme` is decoded as a `String`; an unknown value → `SYSTEM`.
  - A failed refresh keeps whatever is cached.

- [ ] **Step 1: Failing tests:**

```kotlin
@Test fun `defaults before any fetch come from brand config`()
@Test fun `fetched config is cached across instances`()
@Test fun `failed refresh keeps the cached config`()
@Test fun `unknown theme and unknown flag keys still load`()      // defaultTheme "SEPIA", features {"x_new": true}
@Test fun `theme is null until chosen then persisted`()
```
- [ ] **Step 2:** `./gradlew :core:data:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Same command. Expected: PASS.
- [ ] **Step 5:** Commit `feat(data): remote app config with cached fallback and theme preference`.

### Task 7: Shell runtime: flags, theme, splash, analytics, feature scope

**Files:**
- Modify `core/shell/src/main/kotlin/com/whitelabel/core/shell/`:
  - `{AppDefinition.kt,AppNavigator.kt,ShellNavHost.kt,FoundationActivity.kt,FoundationApplication.kt}`
  - `brand/ShellModule.kt`
- Create `.../ShellViewModel.kt`.
- Modify `apps/globex/notes/.../Notes.kt` and `diagnostics/Diagnostics.kt` for the new `routes` signature.
- Tests: `core/shell/src/test/.../{AppDefinitionTest.kt,ShellViewModelTest.kt}` and `core/shell/src/androidTest/.../ShellNavHostTest.kt`
- Docs: update `docs/adding-a-shared-feature.md` (`FeatureScope`, `requiresSignIn`, analytics events) and `docs/architecture.md` (new modules, and the network → datastore edge removed).

**Interfaces:**
- Consumes: Tasks 2, 5 and 6.
- Produces:
  - `class FeatureScope internal constructor(val navigator: AppNavigator, val deepLinkScheme: String)`. `FeatureEntry.routes` becomes `NavGraphBuilder.(FeatureScope) -> Unit`.
  - `FeatureEntry` gains `requiresSignIn: Boolean = false` and `gate: Gate? = null`, with `class Gate(val startRoute: Any)`. Part B uses `gate`.
  - `AppDefinitionBuilder` gains `analytics(analytics: Analytics)` and `crashReporter(reporter: CrashReporter)`. `build()` throws `IllegalArgumentException`:
    - when an entry with `requiresSignIn` exists and no entry has a `gate`, with the message `"<ids> need sign-in: add auth() to the app definition"`;
    - when there is more than one gate.
  - `AppNavigator.canOpenTab(route: Any): Boolean` (true when that tab is visible).
  - `internal data class ShellState(val ready: Boolean, val visibleTabs: List<Tab>, val theme: ThemePreference, val session: SessionState)`
  - `@HiltViewModel internal class ShellViewModel`, which:
    - on init, runs `withTimeoutOrNull(2_000) { appConfig.refresh() }`;
    - then fixes `visibleTabs` from `config.features` and `brand.runtimeDefaults.enabledFeatures`, and sets `ready = true`;
    - sets `theme = preference ?: config.defaultTheme`;
    - when `session` is SignedIn, calls `userRepository.refresh()` once.
  - `FoundationActivity`:
    - `setKeepOnScreenCondition { !ready || (hasGate && session == Unknown) }`
    - `AppTheme(darkTheme = …)` from `theme`
    - `screen_view` tracked on each destination change, with param `screen` = the route class's simple name
  - `ShellModule` provides `Analytics` = `definition.analytics ?: LoggingAnalytics { Log.d("Analytics", it) }`, and likewise `CrashReporter`.
  - `FoundationApplication` injects `CrashReporter` and chains `Thread.setDefaultUncaughtExceptionHandler`, recording first.

- [ ] **Step 1: Failing tests:**

```kotlin
// AppDefinitionTest
@Test fun `feature needing sign in without auth fails naming auth()`()
@Test fun `two gates fail`()
// ShellViewModelTest (fakes; StandardTestDispatcher)
@Test fun `not ready until the config refresh returns`()
@Test fun `slow config gives up after two seconds with the cached flags`()   // refresh suspends forever; advanceTimeBy(2_001) → ready
@Test fun `tabs stay fixed when the config changes later`()
@Test fun `theme falls back to the config default`()
@Test fun `signed in start refreshes the user once`()
```

  Update `ShellNavHostTest` for `FeatureScope`.
- [ ] **Step 2:** `./gradlew :core:shell:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** `./gradlew :core:shell:testDebugUnitTest :core:shell:connectedDebugAndroidTest`. Expected: PASS.
- [ ] **Step 5:** Commit `feat(shell): remote flags, theme preference and analytics`.
- [ ] **Step 6: Part A checkpoint.** Smoke: both apps start with the backend running and with it stopped, leaving the splash within about 2 s. Diagnostics ping works. Logcat shows `screen_view`. PR title: `feat: phase 4a core session layer`.

---

## Part B: authentication and the gate (`feat/auth`)

### Task 8: Gate and deep links in the shell

**Files:**
- Modify `core/shell/src/main/AndroidManifest.xml`:
  - the activity becomes `launchMode="singleTop"`;
  - add an intent-filter for `VIEW`/`DEFAULT`/`BROWSABLE` with `android:scheme="${deepLinkScheme}"`.
- Modify `core/shell/build.gradle.kts`: `defaultConfig.manifestPlaceholders["deepLinkScheme"] = "foundation"`, for the module's own test APK; the app plugin already overrides it.
- Modify `ShellNavHost.kt`, `ShellViewModel.kt` and `FoundationActivity.kt` (`onNewIntent`).
- Create `.../DeepLinkPolicy.kt`.
- Tests: `core/shell/src/test/.../DeepLinkPolicyTest.kt` and `core/shell/src/androidTest/.../GateTest.kt`

**Interfaces:**
- Produces:
  - `@Serializable internal data object AuthGraph` and `MainGraph`.
  - With a gate, the root `NavHost` has `navigation<AuthGraph>(startDestination = gate.startRoute)`, holding the gate entry's routes, and `navigation<MainGraph>(startDestination = startTab.route)`, holding the others. Its start is decided after the splash.
  - On a session change: SignedIn → `navigate(MainGraph)`; SignedOut → `navigate(AuthGraph)`. Both pop the whole back stack (`popUpTo(graph.id) { inclusive = true }`).
  - `internal enum class DeepLinkAction { OPEN, HOLD, IGNORE }`
  - `internal fun deepLinkAction(inMain: Boolean, inAuth: Boolean, signedIn: Boolean, hasGate: Boolean): DeepLinkAction`:
    - no gate → main links OPEN;
    - main link and signed out → HOLD;
    - auth link and signed in → IGNORE;
    - no match → IGNORE.
  - The held URI lives in `ShellViewModel`'s `SavedStateHandle` and is opened with `navController.handleDeepLink` right after the switch to `MainGraph`.

- [ ] **Step 1: Failing tests:**
  - A table test for `deepLinkAction`.
  - Instrumented tests (test routes, a fake `SessionRepository` with a settable state):

```kotlin
@Test fun signedOut_startsAtGateRoute()
@Test fun signIn_switchesToMainWithNoBackStack()          // navController.previousBackStackEntry == null
@Test fun signOut_returnsToGateAndDropsMainStack()
@Test fun mainDeepLinkWhileSignedOut_opensAfterSignIn()
@Test fun authDeepLinkWhileSignedIn_isIgnored()
```
- [ ] **Step 2:** `./gradlew :core:shell:testDebugUnitTest :core:shell:connectedDebugAndroidTest`. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Same command. Expected: PASS.
- [ ] **Step 5:** Commit `feat(shell): whole-app sign-in gate with held deep links`.

### Task 9: Auth validation and password-reset data

**Files:**
- Create `feature/authentication/build.gradle.kts` (per `docs/adding-a-shared-feature.md`; plus `:core:ui`, `:core:domain`, `:core:network`, `:core:analytics`) and include it.
- Create `core/domain/src/main/kotlin/com/whitelabel/core/domain/Validation.kt` (shared by auth and profile; test in `core/domain/src/test/.../ValidationTest.kt`).
- Create in `feature/authentication/src/main/kotlin/com/whitelabel/feature/authentication/`:
  - `domain/{AuthValidator.kt,PasswordResetDataSource.kt}`
  - `data/{KtorPasswordResetDataSource.kt,AuthDataModule.kt}`
- Tests: `src/test/.../domain/AuthValidatorTest.kt` and `src/test/.../data/KtorPasswordResetDataSourceTest.kt`

**Interfaces:**
- Produces:
  - In `:core:domain`, `object PasswordPolicy { const val MIN = 10; const val MAX = 128; fun check(raw: String): PasswordError? }` (`TOO_SHORT`, `TOO_LONG`; never trimmed). Also `object NamePolicy { const val MAX = 50; fun check(raw: String): NameError? }` (`TOO_LONG` after trim; blank is allowed and means `null`).
  - `internal object AuthValidator { fun email(raw: String): EmailError? }` (`BLANK`, `INVALID`), which validates `raw.trim()` against `^[^@\s]+@[^@\s]+\.[^@\s]+$`. Registration and reset use `PasswordPolicy` and `NamePolicy`.
  - `internal interface PasswordResetDataSource { suspend fun requestReset(email: String): EmptyResult<DataError>; suspend fun reset(token: String, newPassword: String): EmptyResult<DataError> }`. It posts `auth/forgot-password` and `auth/reset-password` publicly.

- [ ] **Step 1: Failing tests:**
  - Validator boundaries:
    - passwords of 9, 10, 128 and 129 chars;
    - a 10-space password is valid;
    - a 25-emoji name is 50 UTF-16 units and valid, while 26 emoji is `TOO_LONG`;
    - `"  a@b.co  "` is valid;
    - `"a@b"` is `INVALID`.
  - Data source:
    - exact request bodies;
    - `INVALID_RESET_TOKEN` comes back as `DataError.Api(INVALID_RESET_TOKEN, 400)`;
    - no `Authorization` header even when signed in.
- [ ] **Step 2:** `./gradlew :core:domain:test :feature:authentication:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Same command. Expected: PASS.
- [ ] **Step 5:** Commit `feat(auth): input validation and password reset data`.

### Task 10: Auth screens and `auth()`

**Files:**
- Create in `feature/authentication/src/main/kotlin/com/whitelabel/feature/authentication/`:
  - `Authentication.kt` (public)
  - `presentation/{login,register,forgot,reset}/*` (Root/Screen/State/Action/ViewModel each)
- Create `res/values{,-fr}/strings.xml`.
- Tests: `src/test/.../presentation/*ViewModelTest.kt` and `src/androidTest/.../LoginScreenTest.kt`

**Interfaces:**
- Consumes: Task 5 `SessionRepository`, Task 8 `Gate`, Task 9.
- Produces:
  - `data class AuthOptions(val allowRegistration: Boolean = true, val allowPasswordReset: Boolean = true)`
  - `fun AppDefinitionBuilder.auth(options: AuthOptions = AuthOptions())`, which adds `FeatureEntry(id = "auth", gate = Gate(LoginRoute), …)`.
  - Internal routes `LoginRoute`, `RegisterRoute`, `ForgotPasswordRoute` and `ResetPasswordRoute(val token: String)`. The reset route has a deep link `"<scheme>://reset-password?token={token}"`, matching the backend's `buildResetLink` when the tenant's `passwordReset.linkBaseUrl` is `<scheme>://reset-password`.
- Behaviour:
  - Submit is ignored while `isSubmitting`.
  - Errors:
    - `INVALID_CREDENTIALS` → `auth_error_invalid_credentials`
    - `EMAIL_ALREADY_REGISTERED` → an error on the email field
    - `INVALID_RESET_TOKEN` → a message plus a "request a new link" action
    - anything else → `toUiText()`
  - Forgot-password shows the same success text whether or not the email exists.
  - Reset success navigates to Login with `auth_reset_done`.
  - `SavedStateHandle` keeps the email (and names on register), never a password.
  - Analytics: `login`/`sign_up` with `{"method" to "password"}` only. `logout` is Part C.
  - The login screen shows `BrandConfig.appName`. The register/forgot links are hidden per options.

- [ ] **Step 1: Failing tests:**

```kotlin
@Test fun `double submit sends one sign in`()                  // fake SessionRepository counts calls; second OnSubmit while submitting ignored
@Test fun `invalid credentials shows the wrong password message`()
@Test fun `password is not kept in saved state`()             // SavedStateHandle keys exclude any password; email restored
@Test fun `analytics never receives the email`()              // fake Analytics: no param value contains the typed email
@Test fun `already registered email marks the email field`()
@Test fun `expired reset token offers a new link`()
// LoginScreenTest (instrumented): type email + password, tap sign in → onAction(OnSubmit) with both values
```
- [ ] **Step 2:** `./gradlew :feature:authentication:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Same command, plus `:feature:authentication:connectedDebugAndroidTest`. Expected: PASS.
- [ ] **Step 5:** Commit `feat(auth): login, registration and password reset screens`.

### Task 11: Apps use `auth()`

**Files:**
- Modify `apps/{showcase,globex}/build.gradle.kts` and both definitions: add `auth()`.
- Docs:
  - create `docs/shared-features.md`, with one section per feature: options, flag key, endpoints and analytics events (auth first);
  - `docs/api-environments.md`: set the tenant's `passwordReset.linkBaseUrl` to `<scheme>://reset-password`;
  - `docs/creating-an-app.md`: picking `auth()`.

- [ ] **Step 1:** `./gradlew assembleDebug :apps:showcase:testDebugUnitTest :apps:globex:testDebugUnitTest`. Expected: PASS.
- [ ] **Step 2:** Commit `feat(apps): sign-in gate in showcase and globex`.
- [ ] **Step 3: Part B checkpoint.** Smoke on globex:
  1. Register.
  2. Kill the app and relaunch; you are still signed in.
  3. Delete the session in the backend (or wait out the token) and relaunch; you are back at login.
  4. Open `adb shell am start -d "globex://reset-password?token=x"`; the reset screen shows, and submitting shows the expired-link message.

  PR title: `feat: phase 4b authentication and sign-in gate`.

---

## Part C: settings (`feat/settings`)

### Task 12: Per-app language plumbing

**Files:**
- Modify:
  - `gradle/libs.versions.toml` (`appcompat = "1.7.1"`)
  - `core/shell/build.gradle.kts`
  - `core/shell/src/main/res/values/themes.xml`: `Theme.Foundation` parent `Theme.AppCompat.DayNight.NoActionBar`
  - `core/shell/src/main/AndroidManifest.xml`: `androidx.appcompat.app.AppLocalesMetadataHolderService` with meta-data `autoStoreLocales=true`
  - `FoundationActivity.kt`: extends `AppCompatActivity`
  - `build-logic/.../AndroidApplicationConventionPlugin.kt`: `androidResources.generateLocaleConfig = true`, plus a task writing `resources.properties` (`unqualifiedResLocale=en`) into a generated res dir added with `variant.sources.res?.addGeneratedSourceDirectory`
- Create `core/domain/.../Language.kt` and `core/data/.../language/AppCompatLanguageRepository.kt` (bound in `DataModule`; `:core:data` adds appcompat).
- Test: `core/data/src/androidTest/.../AppCompatLanguageRepositoryTest.kt`

**Interfaces:**
- Produces: `interface LanguageRepository { fun current(): String?; fun set(languageTag: String?) }`. `null` means the system default.

- [ ] **Step 1: Failing test:** `set_fr_thenCurrentIsFr`, then `set_null_thenCurrentIsNull`.
- [ ] **Step 2:** `./gradlew :core:data:connectedDebugAndroidTest`. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Same command, plus `assembleDebug && scripts/verify-apps.sh`. Expected: PASS. `aapt2 dump xmltree` of a debug APK shows `android:localeConfig`.
- [ ] **Step 5:** Commit `feat(shell): per-app language support`.

### Task 13: Settings data and ViewModel

**Files:**
- Create `feature/settings` (dependencies as Task 9's module) and include it.
- Create in `feature/settings/src/main/kotlin/com/whitelabel/feature/settings/`:
  - `data/{KtorSettingsDataSource.kt,SettingsDto.kt,SettingsDataModule.kt}`
  - `domain/{NotificationSettings.kt,SettingsDataSource.kt}`
  - `presentation/{SettingsState.kt,SettingsAction.kt,SettingsViewModel.kt}`
- Tests: `src/test/.../SettingsViewModelTest.kt` and `src/test/.../KtorSettingsDataSourceTest.kt`

**Interfaces:**
- Consumes: Tasks 5, 6 and 12.
- Produces:
  - `internal data class NotificationSettings(val push: Boolean, val email: Boolean, val marketing: Boolean)`
  - `internal interface SettingsDataSource { suspend fun get(): Result<NotificationSettings, DataError>; suspend fun patch(patch: SettingsPatch): EmptyResult<DataError> }`. `SettingsPatch` has nullable `theme`, `language`, `push`, `email` and `marketing`; null fields are omitted.
  - `SettingsState` holds:
    - `theme`, `language`, `languages: List<String>`;
    - `notifications: NotificationSettings?` and `notificationsError: UiText?`;
    - `signedIn`;
    - `appName`, `versionName`, `supportEmail`, `privacyUrl`, `termsUrl`;
    - `showDiagnostics` (environment == DEVELOPMENT).
- Behaviour:
  - Offered languages are `options.languages` ∩ `config.supportedLanguages`, or `options.languages` when the remote list is empty.
  - Theme and language apply locally first, then `patch` best effort when signed in.
  - A notification toggle is optimistic. PATCHes run in order under a `Mutex`, and a failure reverts that toggle and shows the error.
  - Notifications load only when signed in.
  - Sign out goes through a confirm dialog, then `session.signOut()` and the `logout` event.
  - Server settings aren't cached, so sign-out has nothing extra to clear.

- [ ] **Step 1: Failing tests:**

```kotlin
@Test fun `theme change applies locally and patches when signed in`()
@Test fun `signed out hides notifications and sends no request`()
@Test fun `failed toggle reverts and shows the error`()
@Test fun `rapid toggles leave the server at the last value`()      // on, off, on → last PATCH body push=true, PATCHes in order
@Test fun `languages are the options filtered by the remote list`()
@Test fun `sign out asks first then signs out`()
```
- [ ] **Step 2:** `./gradlew :feature:settings:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Same command. Expected: PASS.
- [ ] **Step 5:** Commit `feat(settings): settings state, sync and sign out`.

### Task 14: Settings UI, `settings()`, diagnostics from About

**Files:**
- Create `feature/settings/.../Settings.kt` (public), `presentation/{SettingsRoot.kt,SettingsScreen.kt}` and strings.
- Modify `core/shell/.../diagnostics/Diagnostics.kt`: delete `diagnostics()`; the shell registers `composable<DiagnosticsRoute>` in the main graph when the environment is DEVELOPMENT.
- Modify both app definitions and build files. Globex becomes `auth(); notes(); settings(); startTab(NotesRoute)`; showcase becomes `auth(); settings()`.
- Test: `feature/settings/src/androidTest/.../SettingsScreenTest.kt`
- Docs: `shared-features.md` (settings), `creating-an-app.md` (no more `diagnostics()`), and `adding-a-shared-feature.md` (its example matches the real `settings()`).

**Interfaces:**
- Produces:
  - `data class SettingsOptions(val showNotifications: Boolean = true, val languages: List<String> = listOf("en", "fr"))`
  - `@Serializable data object SettingsRoute`
  - `fun AppDefinitionBuilder.settings(options: SettingsOptions = SettingsOptions())`, which adds a `FeatureEntry(id = "settings", tab = Tab(SettingsRoute, R.string.settings_tab, Icons.Default.Settings))`.
  - Sections:
    - Appearance (theme, language)
    - Notifications (when `showNotifications` and signed in)
    - About:
      - version;
      - support, via `openUrl("mailto:…")`;
      - privacy and terms, via `openUrl`;
      - Diagnostics, via `navigator.navigate(DiagnosticsRoute)` when `showDiagnostics`.
    - Account (sign out, when signed in)

- [ ] **Step 1: Failing test:** `selectDarkTheme_sendsThemeAction`, and `signOut_confirmDialog_thenAction`.
- [ ] **Step 2:** `./gradlew :feature:settings:connectedDebugAndroidTest`. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Same command, plus `assembleDebug assembleRelease`. Expected: PASS.
- [ ] **Step 5:** Commit `feat(settings): settings screen in both apps`.
- [ ] **Step 6: Part C checkpoint.** Smoke on both apps:
  - the theme switches instantly and survives a restart;
  - French switches the UI language;
  - a notification toggle persists;
  - About › Diagnostics exists in debug and not in release;
  - sign out returns to login.

  PR title: `feat: phase 4c settings`.

---

## Part D: profile (`feat/profile`)

### Task 15: Profile view and edit

**Files:**
- Create `feature/profile` (as Task 9) with `Profile.kt` and `presentation/profile/*`.
- Modify `core/data/.../user/DefaultUserRepository.kt` (`update`).
- Tests: `core/data/src/test/.../DefaultUserRepositoryTest.kt` (added cases), `feature/profile/src/test/.../ProfileViewModelTest.kt` and `feature/profile/src/androidTest/.../ProfileScreenTest.kt`

**Interfaces:**
- Produces:
  - `data class ProfileOptions(val allowPasswordChange: Boolean = true)`
  - `@Serializable data object ProfileRoute`
  - `fun AppDefinitionBuilder.profile(options: ProfileOptions = ProfileOptions())`, which adds a `FeatureEntry(id = "profile", flagKey = "profile", requiresSignIn = true, tab = Tab(ProfileRoute, R.string.profile_tab, Icons.Default.Person))`.
  - `UserRepository.update` sends `PATCH users/me` as a `JsonObject` with an explicit `null` for a cleared name, then caches the returned user.
  - The screen shows the email read-only and edits the first and last name, checked with `NamePolicy`. Names are trimmed, and blank becomes `null`. Save is enabled only when something changed and is valid.

- [ ] **Step 1: Failing tests:**

```kotlin
@Test fun `clearing a name sends an explicit null`()               // request body {"firstName":null,"lastName":"Doe"}
@Test fun `update caches the returned user`()
@Test fun `save is disabled until a valid change`()
@Test fun `name over fifty characters shows the length error`()
// ProfileScreenTest: edit first name, save → OnSave action
```
- [ ] **Step 2:** `./gradlew :core:data:testDebugUnitTest :feature:profile:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Same, plus `:feature:profile:connectedDebugAndroidTest`. Expected: PASS.
- [ ] **Step 5:** Commit `feat(profile): view and edit the profile`.

### Task 16: Change password and wiring

**Files:**
- Create `feature/profile/.../presentation/password/*` and `data/{KtorPasswordDataSource.kt,ProfileDataModule.kt}`.
- Modify the showcase definition (`profile()`; `startTab` stays the first tab) and `shared-features.md`.
- Tests: `feature/profile/src/test/.../ChangePasswordViewModelTest.kt` and `.../KtorPasswordDataSourceTest.kt`

**Interfaces:**
- Produces:
  - `internal interface PasswordDataSource { suspend fun change(current: String, new: String): EmptyResult<DataError> }`, which calls `PATCH users/me/password` (authenticated).
  - The screen has current, new and confirm fields. It validates the new password with `PasswordPolicy` (`:core:domain`) and requires confirm to equal new.
  - `INVALID_CREDENTIALS` → `profile_error_wrong_current_password`. Success shows `profile_password_changed` ("Other devices were signed out") and goes back.
  - The entry point is a row on the profile screen, hidden when `!allowPasswordChange`.

- [ ] **Step 1: Failing tests:** `` `wrong current password shows its message and keeps the session` `` (fake session state remains SignedIn), `` `mismatched confirmation blocks submit` ``, `` `double submit sends one request` ``, and the data source's body is exact.
- [ ] **Step 2:** `./gradlew :feature:profile:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Same command, plus `assembleDebug`. Expected: PASS.
- [ ] **Step 5:** Commit `feat(profile): change password`.
- [ ] **Step 6: Part D checkpoint.** Smoke on showcase:
  - edit and clear a name;
  - a wrong current password shows its message and stays signed in;
  - a successful change keeps you signed in;
  - `npm run tenant -- set-feature --slug acme --key profile --enabled false` → after a relaunch, the Profile tab is gone.

  PR title: `feat: phase 4d profile`.

---

## Part E: home (`feat/home`)

### Task 17: Home dashboard

**Files:**
- Create `feature/home` with `Home.kt` and `presentation/*`.
- Modify the showcase definition: `home(HomeOptions(shortcuts = listOf(HomeShortcut(R.string.showcase_shortcut_profile, Icons.Default.Person, ProfileRoute))))`, `startTab(HomeRoute)`. Its strings live in `apps/showcase/src/main/res`.
- Tests: `feature/home/src/test/.../HomeViewModelTest.kt` and `feature/home/src/androidTest/.../HomeScreenTest.kt`
- Docs: `shared-features.md` (home).

**Interfaces:**
- Produces:
  - `data class HomeShortcut(@StringRes val label: Int, val icon: ImageVector, val tab: Any)`
  - `data class HomeOptions(val shortcuts: List<HomeShortcut> = emptyList())`
  - `@Serializable data object HomeRoute`
  - `fun AppDefinitionBuilder.home(options: HomeOptions = HomeOptions())`, which adds a `FeatureEntry(id = "home", requiresSignIn = true, tab = Tab(HomeRoute, R.string.home_tab, Icons.Default.Home))`.
- Behaviour:
  - The greeting is `home_greeting_named` with the first name, or `home_greeting` when the name is null or blank.
  - Shortcuts are filtered by `navigator.canOpenTab(tab)`, and a click calls `toTab`.
  - Pull to refresh calls `userRepository.refresh()`, and an error shows as a snackbar (`toUiText()`).

- [ ] **Step 1: Failing tests:** `` `greets by first name` ``, `` `blank first name uses the plain greeting` ``, `` `refresh error shows a message and keeps the cached user` ``, and UI `hiddenTabShortcut_isNotShown`.
- [ ] **Step 2:** `./gradlew :feature:home:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Same command, plus `:feature:home:connectedDebugAndroidTest assembleDebug`. Expected: PASS.
- [ ] **Step 5:** Commit `feat(home): home dashboard with app-chosen shortcuts`.
- [ ] **Step 6: Part E checkpoint.** Smoke on showcase: the greeting shows, the Profile shortcut opens the Profile tab, and with `profile` switched off the shortcut is hidden. PR title: `feat: phase 4e home`.

---

## Part F: catalog (`feat/catalog`)

### Task 18: Catalog data and list state

**Files:**
- Create `feature/catalog` with:
  - `domain/{CatalogItem.kt,CatalogQuery.kt,CatalogDataSource.kt}`
  - `data/{KtorCatalogDataSource.kt,CatalogItemDto.kt,CatalogDataModule.kt}`
  - `presentation/list/{CatalogListState.kt,CatalogListAction.kt,CatalogListViewModel.kt}`
- Tests: `src/test/.../KtorCatalogDataSourceTest.kt` and `src/test/.../CatalogListViewModelTest.kt`

**Interfaces:**
- Produces:
  - `internal data class CatalogItem(val id: String, val title: String, val description: String?, val category: String?, val imageUrl: String?)`
  - `enum class CatalogSort(val wire: String) { NEWEST("createdAt:desc"), OLDEST("createdAt:asc"), TITLE_ASC("title:asc"), TITLE_DESC("title:desc") }` (public: used in options)
  - `internal data class CatalogQuery(val text: String = "", val category: String? = null, val sort: CatalogSort)`
  - `internal interface CatalogDataSource { suspend fun page(query: CatalogQuery, page: Int, pageSize: Int): Result<Page<CatalogItem>, DataError>; suspend fun item(id: String): Result<CatalogItem, DataError> }`. A blank `text` and a null category are omitted, and `text` is trimmed and capped at 100.
  - `CatalogListState` holds `query`, `items`, `isLoadingFirst`, `isLoadingMore`, `endReached`, `firstPageError: UiText?`, `moreError: UiText?` and `isRefreshing`.
  - Actions: `OnQueryChange`, `OnCategorySelect`, `OnSortSelect`, `OnNearEnd`, `OnRetry`, `OnRefresh` and `OnItemClick`.
- Behaviour:
  - Query changes are debounced 300 ms, and an empty query is not debounced. Each new query cancels the in-flight load (`collectLatest`), so stale results never land.
  - `OnNearEnd` loads `page + 1` unless loading, `endReached` or `moreError` is set. A page with fewer than `pageSize` items, or `page >= totalPages`, sets `endReached`.
  - `FEATURE_DISABLED` → `catalog_unavailable` (empty state, no retry).
  - The `search` event fires once per settled non-blank query, with `{"length" to n}`, never the text.

- [ ] **Step 1: Failing tests:**

```kotlin
@Test fun `query, category and sort reach the request`()           // MockEngine asserts q, category, sort, page, pageSize
@Test fun `blank query is omitted and long query is capped at 100`()
@Test fun `older query results never replace newer ones`()         // first query's response delayed past the second's
@Test fun `near end loads the next page once`()                    // two OnNearEnd while loading → one request
@Test fun `short page ends the list`()
@Test fun `more error keeps items and retries on demand`()
@Test fun `feature disabled shows unavailable without retry`()
@Test fun `search event carries length not text`()
```
- [ ] **Step 2:** `./gradlew :feature:catalog:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Same command. Expected: PASS.
- [ ] **Step 5:** Commit `feat(catalog): paginated search with filters and sort`.

### Task 19: Catalog UI, details, deep link and wiring

**Files:**
- Modify `gradle/libs.versions.toml` (`coil = "3.3.0"`, `coil-compose`, `coil-network-okhttp`).
- Create in `feature/catalog/`: `Catalog.kt`, `presentation/list/{CatalogListRoot.kt,CatalogListScreen.kt}`, `presentation/detail/*` and strings.
- Modify the showcase definition: `catalog(CatalogOptions(categories = listOf("featured", "new")))`, plus a home shortcut to `CatalogRoute`.
- Tests: `feature/catalog/src/test/.../CatalogDetailViewModelTest.kt` and `feature/catalog/src/androidTest/.../CatalogListScreenTest.kt`
- Docs: `shared-features.md` (catalog), `architecture.md` (every Phase 4 module marked built) and the `README.md` quick start (sign-in step).

**Interfaces:**
- Produces:
  - `data class CatalogOptions(val categories: List<String> = emptyList(), val pageSize: Int = 20, val defaultSort: CatalogSort = CatalogSort.NEWEST)`. Its `init` requires `pageSize in 1..100` and each category length 1..50.
  - `@Serializable data object CatalogRoute`
  - `fun AppDefinitionBuilder.catalog(options: CatalogOptions = CatalogOptions())`, which adds a `FeatureEntry(id = "catalog", flagKey = "catalog", requiresSignIn = true, tab = Tab(CatalogRoute, R.string.catalog_tab, Icons.AutoMirrored.Filled.List))`.
  - The internal `CatalogDetailRoute(val id: String)` has the deep link `"<scheme>://catalog/{id}"`.
  - The list screen has:
    - a search field;
    - category chips (hidden when `categories` is empty);
    - a sort menu;
    - pull to refresh;
    - loading, empty, error and footer-retry states;
    - Coil `AsyncImage` with a placeholder for a missing or failed image.
  - The detail screen tracks `view_item` with `{"item_id" to id}`, and `NOT_FOUND` → `catalog_item_not_found`.

- [ ] **Step 1: Failing tests:** detail `` `not found shows the missing item message` `` and `` `view item event fires once` ``; UI `typingQuery_showsResults`, `emptyResult_showsEmptyState` and `footerError_retryLoadsMore` (fake data source through a test Hilt module or direct ViewModel construction).
- [ ] **Step 2:** `./gradlew :feature:catalog:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Same command, plus `:feature:catalog:connectedDebugAndroidTest assembleDebug assembleRelease`. Expected: PASS.
- [ ] **Step 5:** Commit `feat(catalog): catalog screens and deep link in showcase`.
- [ ] **Step 6: Part F and Phase 4 checkpoint.** Smoke on showcase:
  - search, filter, sort and scroll past one page;
  - `adb shell am start -d "showcase://catalog/<id>"` while signed out opens the item right after sign-in;
  - `set-feature --key catalog --enabled false` → after a relaunch, the tab and the home shortcut are gone.

  Smoke on globex: Notes and Settings only, with sign-in. Run every module's `connectedDebugAndroidTest`. PR title `feat: phase 4f catalog`. The report includes screenshots of every showcase tab and of globex. Next comes the Phase 5 plan (integration and quality).
