# Integration and Quality (Phase 5) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Pin the app to the backend contract with tests, add a debug-only mock backend for working without a server, test each app end to end on an emulator, and run all of it in CI.

**Architecture:**
- **The contract is vendored.** `api/openapi.json` (a backend tag) is the source of truth. A JVM test-fixtures module loads it and validates JSON against its schemas.
- **Contract tests per module.** Each data module serves the spec's examples through its real Ktor data source, and checks that what it sends matches the request schemas.
- **A mock backend.** `:core:mockapi` is an in-memory fake of the backend-starter API behind a Ktor `MockEngine`. The shell uses it when it is on the classpath (an optional Hilt binding), and every response it gives is validated against the spec.
- **E2E tests.** Each app's tests drive `FoundationActivity` against that fake, with Hilt testing and the Robot pattern.
- **CI** gains an emulator job and a check that the vendored spec hasn't been edited.

**Tech Stack:**
- As in Phase 4: Kotlin 2.2.20, AGP 8.13.2, Compose BOM 2025.10.01, Hilt 2.57.2, Ktor 3.3.1 (incl. `ktor-client-mock`), JUnit 5, AssertK.
- New:
  - `com.google.dagger:hilt-android-testing` 2.57.2 (same version as Hilt);
  - `com.networknt:json-schema-validator` 1.5.6 (JSON Schema 2020-12, which OpenAPI 3.1 uses);
  - GitHub Action `reactivecircus/android-emulator-runner@v2`.

**Spec:** `docs/superpowers/specs/2026-11-10-reusable-app-foundation-design.md` §7 (Phase 5 row: "mock backend (debug-only), OpenAPI contract tests, E2E tests per app, emulator CI job, final docs"). It builds on `2026-11-09-whitelabel-android-design.md` §7 (vendored spec, `sync-openapi.sh`, contract tests from the spec's examples, mock backend behind `-Pwhitelabel.mockApi=true` and absent from release builds, `docs/api-integration.md`) and on brief §10–§12. Standalone app repos (`2026-11-10-standalone-app-support-design.md`) get the same tools through `gradle/foundation.settings.gradle.kts`.

**Out of scope:**
- The 35 deferred Phase 4 minors (tracked separately).
- Release signing in CI and publishing to a store.
- Screenshot tests.
- Running against a real backend in CI (the fake is the CI backend).

## Decisions this plan makes (owner: please confirm in review)

1. **One fake, two uses.** The same `FakeBackend` serves `-Pwhitelabel.mockApi=true` debug builds and the E2E tests. To keep it honest, every response it produces in its own tests is validated against the vendored spec's schemas. This is the guard against the Phase 4 refresh-bug class: a test double that matches our code but not the contract.
2. **The mock reaches the shell through an optional binding, not a source set.** `:core:network` declares `fun interface EngineOverride { fun engine(): HttpClientEngine }`, and `ShellModule` uses it when bound (`@BindsOptionalOf`), else OkHttp. `:core:mockapi` binds it. The app plugin adds `:core:mockapi` as `debugImplementation` only when `whitelabel.mockApi=true`, and E2E tests add it as `androidTestImplementation`. Release builds can't contain it, and `verify-apps.sh` asserts that.
3. **Contract tests go through the real data sources.** No separate DTO harness: each module serves the spec's examples through `MockEngine` to its real `Ktor*DataSource`, and validates the request body it sent. Our DTOs are `private`/`internal`, so these tests live in each module's `src/test`.
4. **Freshness is an integrity check, not a network check.** `api/SOURCE` records the backend tag, commit and the file's SHA-256. CI fails if `api/openapi.json` no longer matches it, which catches a hand edit. Moving to a newer tag is a deliberate `scripts/sync-openapi.sh <tag>` plus a PR. CI has no access to the backend repo, so "is there a newer tag" stays manual and is documented.
5. **E2E uses Hilt testing.** `@HiltAndroidTest` with `HiltTestApplication`, through a shared runner. The fake is injected into the test, which controls it: feature flags, expiring tokens, revoking sessions, going offline.

## Global Constraints

- **Everything in the Phase 4 plan's Global Constraints still holds:** versions only from `gradle/libs.versions.toml`, the dependency rules, no app identity in shared code, plugin order, strings in English and French, and passwords never logged or sent to analytics.
- **Release builds never contain mock code.** No `com/whitelabel/core/mockapi` classes in any release dex, checked by `verify-apps.sh`. Production flows never depend on mock data (brief §10).
- **New modules:**
  - `:core:testing`: JVM; `com.whitelabel.core.testing`; used only as `testImplementation`.
  - `:core:mockapi`: Android + Hilt; `com.whitelabel.core.mockapi`.
  - `:core:testing-android`: Android; `com.whitelabel.core.testing.android`; used only as `androidTestImplementation`.

  All three are added to `gradle/foundation.settings.gradle.kts`, so app repos get them.
- **The fake's seed data mirrors the backend seed:**
  - users `alice@shared.test` and `bob@shared.test`, password `Password123!`, first names Alice and Bob;
  - 25 catalog items titled `Item 1`…`Item 25`, in categories `Books`, `Games`, `Music`, `Travel`, in rotation;
  - app config with `catalog` and `profile` on, `defaultTheme` `SYSTEM`, and `supportedLanguages` `["en", "fr"]`;
  - access tokens live 900 s; refresh tokens are single-use, rotated, and reuse is rejected with `INVALID_REFRESH_TOKEN`.
- **Pinned contract:** backend tag `v1.1.1` at the start of this plan.
- **Git** follows `CLAUDE.md`, with one branch and one PR per part:

  | Part | Branch |
  |---|---|
  | A | `feat/api-contract` |
  | B | `feat/mock-backend` |
  | C | `feat/e2e` |
  | D | `ci/instrumented` (postponed until after the KMP migration) |

  Each part ends with the Phase 4 plan's part checkpoint (check, both builds, `verify-apps.sh`, the fixture build, touched modules' connected tests, smoke, docs, PR, CI green, report).
- **E2E tests** follow the `android-ui-testing` skill: `createAndroidComposeRule`, a Robot per screen, and no `Thread.sleep`; wait with `waitUntil` on semantics.

## Review Focus

1. **The fake drifting from the contract.** E2E would stay green while the real app breaks. Every fake response in Task 5's tests is schema-validated, and Task 3 runs the spec's own examples through the real data sources. Pinned in Tasks 3 and 5.
2. **Mock code in a release build**, or a release build that could bind the fake engine. Pinned in Task 6 (`verify-apps.sh` dex check, plus a build-logic test that the dependency is debug-only and conditional).
3. **A stale or hand-edited contract.** `api/openapi.json` changed without `sync-openapi.sh` must fail CI with a message naming the script. Pinned in Task 1.
4. **E2E tests leaking state into each other** (stored tokens, DataStore files, the fake's sessions) makes them flaky or order-dependent. Each test must start signed out with a fresh fake. Pinned in Task 7, by two tests that each assert a clean start.
5. **A session expiring or being revoked mid-use, end to end:** the real Keystore token store, the session plugin and the backend's rotation rules together. Pinned in Task 8 (`expired access token refreshes silently`, `revoked session returns to sign-in`).

---

## Part A: the contract (`feat/api-contract`)

### Task 0: Branch and commit the plan

- [ ] **Step 1:** `git switch main && git pull && git switch -c feat/api-contract`.
- [ ] **Step 2:** Commit this plan as `docs: phase 5 integration and quality plan`.

### Task 1: Vendored spec and its scripts

**Files:**
- Create:
  - `api/openapi.json` (copied from `../backend-api` at tag `v1.1.1`)
  - `api/SOURCE`
  - `scripts/sync-openapi.sh`
  - `scripts/check-openapi.sh`
  - `scripts/test-openapi-scripts.sh`
- Modify: `.github/workflows/ci.yml` (run `scripts/check-openapi.sh` in the `ci` job, before the unit tests).

**Interfaces:**
- Produces:
  - `api/SOURCE`: three `key=value` lines, `tag=v1.1.1`, `commit=<40-hex sha>` and `sha256=<hex of api/openapi.json>`.
  - `scripts/sync-openapi.sh <tag> [backend-dir]`: `backend-dir` defaults to `../backend-api`. It runs `git -C <dir> show <tag>:openapi/openapi.json`, writes `api/openapi.json`, and rewrites `api/SOURCE`. It exits non-zero, with a message, if the tag doesn't exist.
  - `scripts/check-openapi.sh`: exits 0 when `api/openapi.json` hashes to `sha256` and `SOURCE` has all three keys. Otherwise it prints `api/openapi.json does not match api/SOURCE: update it with scripts/sync-openapi.sh <tag>, never by hand` and exits 1.

- [ ] **Step 1: Failing test.** `scripts/test-openapi-scripts.sh` works in a temp dir holding a throwaway git repo with an `openapi/openapi.json` tagged `t1`. It asserts:
  - sync then check → 0;
  - one byte changed in `api/openapi.json` → check exits 1 and prints "sync-openapi.sh";
  - a `SOURCE` missing `commit` → 1;
  - sync with an unknown tag → non-zero, and `api/` unchanged.
- [ ] **Step 2:** `scripts/test-openapi-scripts.sh`. Expected: FAIL (scripts missing).
- [ ] **Step 3:** Implement both scripts (POSIX `sh`; `shasum -a 256` on macOS, `sha256sum` on Linux, picking whichever exists). Then run `scripts/sync-openapi.sh v1.1.1`.
- [ ] **Step 4:** `scripts/test-openapi-scripts.sh && scripts/check-openapi.sh`. Expected: both PASS.
- [ ] **Step 5:** Commit `feat(api): vendor the backend contract with an integrity check`.

### Task 2: `:core:testing`, the spec as test fixtures

**Files:**
- Create:
  - `core/testing/build.gradle.kts` (jvm library; `api(libs.kotlinx.serialization.json)`, `api(libs.json.schema.validator)`)
  - `core/testing/src/main/kotlin/com/whitelabel/core/testing/OpenApiSpec.kt`
  - its test `core/testing/src/test/.../OpenApiSpecTest.kt`
- Modify: `gradle/libs.versions.toml` (`jsonSchemaValidator = "1.5.6"`), `gradle/foundation.settings.gradle.kts`, and `build-logic/.../ProjectExtensions.kt`, whose `addTestDependencies` sets the system property below on every `Test` task.

**Interfaces:**
- Produces, in `object OpenApiSpec`:
  - `val json: JsonObject`, loaded once from the path in system property `whitelabel.openapi`. The convention plugins set it to `project(":core:testing").projectDir.resolve("../../api/openapi.json")`, so it resolves in this repo and in app repos (where `core/testing` is under `foundation/`).
  - `fun responseExample(path: String, method: String, status: Int): String`. `path` is as in the spec, e.g. `/api/v1/catalog`. It returns the example as JSON text and fails the test, naming path/method/status, when there is none.
  - `fun requestExample(path: String, method: String): String?`
  - `fun responseErrors(path: String, method: String, status: Int, body: String): List<String>`, which validates `body` against that response's schema with `$ref`s resolved inside the spec. Empty means valid.
  - `fun requestErrors(path: String, method: String, body: String): List<String>`
  - `fun operations(): List<Pair<String, String>>`: every (path, method) in the spec.

- [ ] **Step 1: Failing tests:**

```kotlin
@Test fun `the health example is valid against its own schema`()      // responseErrors(.., 200, responseExample(..)) is empty
@Test fun `a body missing a required field is reported`()              // TokensEnvelope without data.tokens.refreshToken → one error naming refreshToken
@Test fun `an unknown path fails naming it`()
@Test fun `every response example in the spec validates`()            // operations() × documented statuses with examples → no errors (proves the validator handles the whole spec)
```
- [ ] **Step 2:** `./gradlew :core:testing:test`. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Same command. Expected: PASS.
- [ ] **Step 5:** Commit `feat(testing): load and validate against the vendored contract`.

### Task 3: Contract tests through the real data sources

**Files:**
- Create, each next to the module's existing tests, a `ContractTest.kt` in:
  - `core/network` (error envelopes, refresh)
  - `core/data` (login, register, `users/me` GET and PATCH, `app/config`, logout)
  - `feature/authentication` (forgot and reset)
  - `feature/settings` (GET and PATCH settings)
  - `feature/profile` (password)
  - `feature/catalog` (list and item)
  - `core/shell` (health)
- Modify: each module's `build.gradle.kts`, adding `testImplementation(project(":core:testing"))`.

**Interfaces:**
- Consumes: Task 2's `OpenApiSpec`, and each module's existing `Ktor*DataSource`, repositories and `HttpClientFactory`.
- Pattern: a `MockEngine` that answers the call with `OpenApiSpec.responseExample(path, method, status)` and records the request.
  - **Success:** the data source returns `Result.Success`, and the test asserts two or three fields from the example (e.g. the first catalog item's `title`).
  - **Request:** `OpenApiSpec.requestErrors(path, method, recordedBody)` is empty.
  - **Errors:** in `core/network`, every documented 4xx/5xx example in the spec, served to `getResult`, maps to `DataError.Api` whose `code` is not `UNKNOWN`, so every documented code is known to the app.

- [ ] **Step 1: Write the tests**, one per operation the module calls, named `` `<method> <path> matches the contract` ``. They exercise code that already exists, so they can pass at once; Step 2 proves they can fail.
- [ ] **Step 2: Prove the tests bite.** Temporarily change `SessionAuth`'s `RefreshResponse` to decode the pair at `data` (the Phase 4 bug). Run `./gradlew :core:network:test --tests '*ContractTest*'`. Expected: FAIL on `post /api/v1/auth/refresh matches the contract`. Revert.
- [ ] **Step 3:** Run every module's `ContractTest`. Expected: PASS. Any failure is a real contract mismatch: fix the DTO, and record it in the ledger.
- [ ] **Step 4: Write `docs/api-integration.md`** (brief §10, old design §7). Cover:
  - the endpoints the app calls, and which module calls each;
  - auth (bearer, the refresh rules, `publicRequest`);
  - the error codes and how each is shown;
  - pagination;
  - `X-Client-Id`;
  - the pinned tag and how to move it (`sync-openapi.sh`, then the contract tests, then a PR);
  - what an app's own endpoints (`/api/v1/x/…`) should do: the same `ContractTest` pattern against its backend's spec.

  Link it from the README and from `docs/adding-a-shared-feature.md` ("Tests").
- [ ] **Step 5:** Commit `test(api): contract tests through every data source`. Then commit `docs: api integration guide`.
- [ ] **Step 6: Part A checkpoint.** PR title: `feat: phase 5a api contract`.

---

## Part B: the mock backend (`feat/mock-backend`)

### Task 4: An optional engine override in the shell

**Files:**
- Create: `core/network/src/main/kotlin/com/whitelabel/core/network/EngineOverride.kt`
- Modify:
  - `core/shell/.../brand/ShellModule.kt`
  - a new `core/shell/.../brand/EngineModule.kt` holding the `@BindsOptionalOf`
  - `core/shell/.../diagnostics/*`: the diagnostics screen shows `Backend: mock` when the override is in use.
- Test: `core/shell/src/test/.../brand/EngineSelectionTest.kt`

**Interfaces:**
- Produces:
  - `fun interface EngineOverride { fun engine(): HttpClientEngine }`
  - `internal fun selectEngine(override: Optional<EngineOverride>, default: () -> HttpClientEngine): HttpClientEngine`
  - `@Provides @Singleton @UsingMockApi fun usingMockApi(override: Optional<EngineOverride>): Boolean`
  - the qualifier `@UsingMockApi`.

- [ ] **Step 1: Failing tests:** `` `no override uses the default engine` `` (the default lambda is called once) and `` `an override replaces it` `` (the default is never called).
- [ ] **Step 2:** `./gradlew :core:shell:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3:** Implement. `ShellModule.httpClientEngine` uses `selectEngine(override) { OkHttp.create() }`.
- [ ] **Step 4:** Same command, plus `assembleDebug`. Expected: PASS; nothing binds the override yet.
- [ ] **Step 5:** Commit `feat(shell): let a debug module replace the http engine`.

### Task 5: `:core:mockapi`, the in-memory backend

**Files:**
- Create:
  - `core/mockapi/build.gradle.kts` (android library + hilt + serialization; `implementation(:core:network)`, `ktor-client-mock`; `testImplementation(:core:testing)`)
  - in `core/mockapi/src/main/kotlin/com/whitelabel/core/mockapi/`: `FakeBackend.kt`, `FakeData.kt` and `MockApiModule.kt`
- Tests: `core/mockapi/src/test/.../FakeBackendTest.kt` and `FakeBackendContractTest.kt`
- Modify: `gradle/foundation.settings.gradle.kts`.

**Interfaces:**
- Produces: `class FakeBackend(now: () -> Instant = Instant::now)` with:
  - `val engine: HttpClientEngine`, a `MockEngine` answering every path under `/api/v1/` that the app calls, with the contract's envelopes, error codes and statuses;
  - `val features: MutableMap<String, Boolean>`, seeded `catalog=true` and `profile=true`;
  - `fun expireAccessTokens()`: the next authenticated call gets 401 `TOKEN_EXPIRED`;
  - `fun revokeSessions()`: the next authenticated call gets 401 `UNAUTHENTICATED`, and the next refresh gets `INVALID_REFRESH_TOKEN`;
  - `var offline: Boolean`: every call throws `IOException`;
  - `val requests: List<String>`: `METHOD path` for every call, for assertions.
- `MockApiModule` (`@InstallIn(SingletonComponent)`) provides `@Singleton FakeBackend` and `EngineOverride { backend.engine }`.
- Behaviour that must match the contract:
  - **Sign-in:** a wrong password → 401 `INVALID_CREDENTIALS`; a short password on register or reset → 400 `VALIDATION_ERROR` with `details[].path`; a duplicate email → 409 `EMAIL_ALREADY_REGISTERED`.
  - **Refresh:** it returns the pair under `data.tokens`. A used or unknown refresh token → 401 `INVALID_REFRESH_TOKEN`.
  - **Password change:** a wrong current password → 401 `INVALID_CREDENTIALS`.
  - **Catalog:** `catalog=false` → 404 `FEATURE_DISABLED`; an unknown id → 404 `NOT_FOUND`. `GET catalog` honours `page`, `pageSize`, `q` (case-insensitive title contains), `category` and `sort`. A page past the end gives empty `data`, with `meta.pagination`.
  - **Missing a session:** an authenticated path without a bearer → 401 `UNAUTHENTICATED`.
  - **Forgot password:** returns 202 whether or not the email exists.

- [ ] **Step 1: Failing tests.** Behaviour tests go through `HttpClientFactory.create(backend.engine, …, tokens)` and the real data sources where possible:

```kotlin
@Test fun `seeded user signs in and the wrong password is refused`()
@Test fun `refresh rotates and a reused refresh token is refused`()
@Test fun `expired access token refreshes through the real session plugin`()   // expireAccessTokens(); users/me still succeeds; requests shows one refresh
@Test fun `catalog pages, searches, filters and sorts like the backend`()       // 25 items: page 3 of pageSize 10 has 5; q="item 1" → 11 items (1, 10–19); category Music → every 4th
@Test fun `catalog switched off answers feature disabled`()
@Test fun `offline throws an io error the client maps to no internet`()
```

  Plus `FakeBackendContractTest`, which scripts a full session: health, config, register, login (wrong, then right), me, patch me, settings get/patch, catalog list/item/not-found/disabled, password wrong/right, refresh, logout. For every response it asserts `OpenApiSpec.responseErrors(path, method, status, body)` is empty (Review Focus 1).
- [ ] **Step 2:** `./gradlew :core:mockapi:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3:** Implement. Route on `method` + `encodedPath`; JSON is built with `buildJsonObject`; the state lives in maps guarded by a `Mutex`.
- [ ] **Step 4:** Same command. Expected: PASS.
- [ ] **Step 5:** Commit `feat(mockapi): in-memory backend that follows the contract`.

### Task 6: `-Pwhitelabel.mockApi=true`, kept out of release

**Files:**
- Modify:
  - `build-logic/.../AndroidApplicationConventionPlugin.kt`: when the Gradle property `whitelabel.mockApi` is `true`, add `debugImplementation(project(":core:mockapi"))`.
  - `scripts/verify-apps.sh`: every release APK's dex has no `com/whitelabel/core/mockapi`; extend the existing dex scan.
  - `docs/api-environments.md`: a "Working without the backend" section.
- Test: `build-logic/convention/src/test/.../MockApiDependencyTest.kt`, a unit test of the pure function below, which the plugin calls.

**Interfaces:**
- Produces:
  - `internal fun mockApiDependency(properties: Map<String, Any?>): Pair<String, String>?`, returning `"debugImplementation" to ":core:mockapi"` only for the value `"true"`;
  - the `verify-apps.sh` check line `OK   <app> release: no mock backend`.

- [ ] **Step 1: Failing tests:** `` `mock api is off by default` ``, `` `mock api true adds a debug-only dependency` `` and `` `any other value is off` `` (`"yes"`, `"TRUE "`, `"false"`).
- [ ] **Step 2:** `./gradlew -p build-logic :convention:test`. Expected: FAIL.
- [ ] **Step 3:** Implement the function, the plugin wiring and the `verify-apps.sh` check.
- [ ] **Step 4:** Run:
  - `./gradlew -Pwhitelabel.mockApi=true assembleDebug assembleRelease && scripts/verify-apps.sh`. Expected: OK; release has no mock.
  - **Negative check:** temporarily add `implementation(project(":core:mockapi"))` to showcase, then `assembleRelease && scripts/verify-apps.sh`. Expected: a FAIL naming showcase. Revert.
- [ ] **Step 5: Smoke.** With `-Pwhitelabel.mockApi=true` and the backend stopped:
  - install showcase and sign in as `alice@shared.test`;
  - Home greets Alice; the catalog pages and searches; Settings › About › Diagnostics shows `Backend: mock`.

  The same build without the flag shows the network error.
- [ ] **Step 6:** Commit `feat(build): opt-in mock backend for debug builds`.
- [ ] **Step 7: Part B checkpoint.** PR title: `feat: phase 5b mock backend`.

---

## Part C: end-to-end tests (`feat/e2e`)

### Task 7: `:core:testing-android` and the app test wiring

**Files:**
- Create:
  - `core/testing-android/build.gradle.kts` (android library; `api(hilt-android-testing)`, `api(androidx-test-runner)`, `api(compose-ui-test-junit4)`)
  - in `core/testing-android/src/main/kotlin/com/whitelabel/core/testing/android/`: `FoundationTestRunner.kt`, `ResetAppDataRule.kt` and `ComposeWaits.kt`
- Modify:
  - `build-logic/.../AndroidApplicationConventionPlugin.kt`:
    - `testInstrumentationRunner = "com.whitelabel.core.testing.android.FoundationTestRunner"`;
    - add `androidTestImplementation` of `:core:testing-android`, `:core:mockapi` and `hilt-android-testing`;
    - add `kspAndroidTest(hilt-compiler)`.
  - `gradle/libs.versions.toml` (`hilt-android-testing`), and `gradle/foundation.settings.gradle.kts`.
- Test: `apps/showcase/src/androidTest/.../CleanStartTest.kt`

**Interfaces:**
- Produces:
  - `class FoundationTestRunner : AndroidJUnitRunner`, which creates `HiltTestApplication`.
  - `class ResetAppDataRule : TestRule`, which deletes the target app's `filesDir/datastore` and its Keystore alias `foundation_session` before the test. Order it before `HiltAndroidRule` with `RuleChain`.
  - `fun ComposeTestRule.waitForText(text: String, timeoutMs: Long = 5_000)` and `fun ComposeTestRule.waitForNoText(text: String, timeoutMs: Long = 5_000)`, both using `waitUntil` on `onAllNodesWithText`.

- [ ] **Step 1: Failing tests** (`@HiltAndroidTest`, `createAndroidComposeRule<FoundationActivity>()`):
  - `firstTest_signsIn_andLeavesASession`;
  - `secondTest_stillStartsSignedOut`: it sees "Sign in to Showcase", and `backend.requests` has no `GET /api/v1/users/me` before sign-in.

  Two tests in one class prove nothing leaks (Review Focus 4).
- [ ] **Step 2:** `./gradlew :apps:showcase:connectedDebugAndroidTest`. Expected: FAIL (runner and rule missing).
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Same command, run twice. Expected: PASS both times.
- [ ] **Step 5:** Commit `feat(testing): hilt test runner and clean-start rule for app e2e tests`.

### Task 8: Showcase end to end

**Files:**
- Create in `apps/showcase/src/androidTest/kotlin/com/whitelabel/apps/showcase/e2e/`:
  - `robots/{SignInRobot,HomeRobot,CatalogRobot,ProfileRobot,SettingsRobot}.kt`
  - `ShowcaseE2ETest.kt`

**Interfaces:**
- Consumes: Task 5's `FakeBackend` (injected with `@Inject`) and Task 7's rules and waits.
- Each robot exposes intention methods (e.g. `signIn(email, password)`, `openTab("Catalog")`, `search(text)`, `assertItems(vararg titles)`) and no assertion on Compose internals.

- [ ] **Step 1: Failing tests:**

```kotlin
@Test fun signIn_greetsTheUser_andSignOutReturnsToSignIn()
@Test fun wrongPassword_staysOnSignIn_withTheMessage()
@Test fun register_landsOnHome()
@Test fun catalog_searchFilterSortAndOpenAnItem()                // "Item 1" → 11 results; Music chip; Title Z–A; open → detail
@Test fun catalog_switchedOff_hidesTabAndShortcut()              // backend.features["catalog"] = false before launch
@Test fun profile_editName_andHomeGreetsTheNewName()
@Test fun changePassword_wrongCurrent_staysSignedIn()
@Test fun expiredAccessToken_refreshesSilently()                 // signed in; backend.expireAccessTokens(); pull to refresh on Home → still greeted; requests has exactly one refresh
@Test fun revokedSession_returnsToSignIn()                       // backend.revokeSessions(); relaunch the activity → sign-in
@Test fun catalogDeepLink_whileSignedOut_opensAfterSignIn()      // launch with Intent(ACTION_VIEW, "showcase://catalog/<seeded id>")
```
- [ ] **Step 2:** `./gradlew :apps:showcase:connectedDebugAndroidTest`. Expected: FAIL (robots missing).
- [ ] **Step 3:** Implement the robots. Fix any product bug a test exposes in its own `fix(...)` commit, with a ledger line.
- [ ] **Step 4:** Same command. Expected: PASS twice in a row.
- [ ] **Step 5:** Commit `test(showcase): end-to-end flows against the mock backend`.

### Task 9: Globex end to end

**Files:** create `apps/globex/src/androidTest/kotlin/com/whitelabel/apps/globex/e2e/{robots/NotesRobot.kt,GlobexE2ETest.kt}`. The sign-in and settings robots move into `:core:testing-android` as `com.whitelabel.core.testing.android.robots`, since both apps use the shared auth and settings screens.

- [ ] **Step 1: Failing tests:** `signIn_addANote_itSurvivesARelaunch`, `resetPasswordLink_withABadToken_offersANewLink`, and `settings_darkTheme_survivesARelaunch`.
- [ ] **Step 2:** `./gradlew :apps:globex:connectedDebugAndroidTest`. Expected: FAIL.
- [ ] **Step 3:** Implement. Move the shared robots, and update the showcase imports.
- [ ] **Step 4:** Run both apps' `connectedDebugAndroidTest`. Expected: PASS.
- [ ] **Step 5:** Commit `test(globex): end-to-end flows against the mock backend`. Update `docs/creating-an-app.md` and `docs/creating-a-standalone-app.md`: a new app adds an `e2e/` package with its own robots, reusing the shared ones.
- [ ] **Step 6: Part C checkpoint.** PR title: `feat: phase 5c end-to-end tests`.

---

## Part D: CI (`ci/instrumented`)

> **Postponed (2026-11-11).** The owner postponed this part until the foundation's migration to
> Kotlin Multiplatform (for iOS) is done, because that changes how shared code and its tests are
> laid out and adds an iOS simulator job. Until then, device tests run locally at checkpoints:
> `./gradlew :apps:showcase:connectedDebugAndroidTest` plus each module's
> `connectedDebugAndroidTest`, on an emulator that no other run is using at the same time.
> Deferred items to revisit then: the 5 s waits in `ComposeWaits`/`Robot` may be too short on CI
> emulators.

### Task 10: Emulator job and final docs

**Files:**
- Modify:
  - `.github/workflows/ci.yml`: a second job, `instrumented`;
  - `docs/testing.md`: the contract, mock and E2E rows, plus the commands;
  - `README.md`: the mock-backend quick start;
  - `CLAUDE.md`: rule 3 no longer says instrumented tests are local-only.

**Interfaces:**
- Produces the job `instrumented`:
  - `runs-on: ubuntu-latest`, with KVM enabled via the documented `udev` rule step;
  - `reactivecircus/android-emulator-runner@v2` with `api-level: 34`, `target: google_apis`, `arch: x86_64`, `disable-animations: true`;
  - `script: ./gradlew connectedDebugAndroidTest --continue`;
  - it uploads `**/build/reports/androidTests/` as an artifact on failure;
  - `timeout-minutes: 60`.

- [ ] **Step 1:** Add the job, and push the branch to open a draft PR. Expected: the `instrumented` job runs every module's instrumented tests and both apps' E2E tests, all green. A failure that passes locally is a real CI difference. Fix it (the usual suspects are animations and timing) and record it in the ledger.
- [ ] **Step 2:** Run it twice (re-run the job). Expected: green both times, which is the flakiness check.
- [ ] **Step 3:** Update the docs. `docs/testing.md` covers:
  - each kind of test and where it runs;
  - `-Pwhitelabel.mockApi=true`;
  - the `FakeBackend` controls;
  - how to add a contract test for an app's own endpoint;
  - how to run E2E locally.
- [ ] **Step 4:** Commit `ci: run instrumented and end-to-end tests on an emulator`, then `docs: testing and ci for phase 5`.
- [ ] **Step 5: Phase 5 checkpoint.**
  - PR title: `ci: phase 5d instrumented tests in ci`.
  - Ask the owner to require the `instrumented` check along with `ci`, once it has passed 3 times. On the free plan this is a rule we keep, as for `ci`.
  - Report: CI run links for both jobs, test counts by kind, and the deferred items.
