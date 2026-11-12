# Debug guest sign-in on the real backend: implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** every Debug build (Android and iOS) shows **Continue as guest (debug)**, which signs in as
the seeded `alice@shared.test` on the real backend as well as on the fake.

**Architecture:** `LoginViewModel` already shows the button whenever a `DebugGuestAccount` is
bound (`getOrNull()` in `AuthenticationKoin`). We bind one from Debug-only code: on Android, the
shell's `src/debug` source set (its strings come from Debug-only resources); on iOS, Swift's
`#if DEBUG` `DebugControls` passes it to `SharedGraph`. The binding goes before the contributors,
so the fake's own binding still replaces it when the fake is linked. On Android, optional keys in
`local.properties` replace the default account.

**Tech Stack:** Kotlin Multiplatform, Koin, AGP variant API (build-logic), SwiftUI.

**Spec:** `docs/superpowers/specs/2026-11-12-debug-guest-real-backend-design.md`

Repo: `~/Whitelabel/android-starter-kit` (the foundation), branch `feat/debug-guest-real-backend`.
Follow its `CLAUDE.md` (Conventional Commits, Claude's `Co-Authored-By` trailer).

## Global Constraints

- Default account: `alice@shared.test` / `Password123!` (what `npm run db:seed` creates).
- Release builds never contain the button, the default account or an override: no Release-variant
  resource, no Release code path, no Swift outside `#if DEBUG`.
- Override keys: `debug.guest.email` and `debug.guest.password` in the build's root
  `local.properties` (gitignored). Both or neither. Values are trimmed, and a blank value counts as
  absent. Android only.
- With the fake backend linked, the guest is the fake's account (`FakeData.GUEST_EMAIL` /
  `FakeData.PASSWORD`), even when override keys are set.
- No backend, `/api/v1`, UI or `LoginViewModel` changes. A failed guest sign-in shows the normal
  sign-in error.
- Logic in shared/Kotlin code. Swift only hands over the account.

## Review Focus

1. **The password leaking into a Release APK.** Expected: `Password123!` and the
   `foundation_debug_guest_*` resources are absent from the Release APK. Pinned by Task 4, Step 2.
2. **Override keys set while the fake is linked.** Expected: the fake's alice wins, not the
   override. Pinned by Task 1's test `a linked contributor's guest account replaces the debug one`.
3. **Only one override key set, or one key blank.** Expected: the build fails and names both keys
   and the file. Pinned by Task 2's tests.
4. **An override password with resource-special characters** (`'`, `"`, `\`, `@`, `%`). Expected:
   it reaches the app unchanged, escaped by the existing `androidStringResource()`. Pinned by
   Task 2's escaping test.
5. **iOS test graphs reused across tests.** Expected: a guest set in one test doesn't leak into the
   next, so `clearDebugGuest()` resets it. Pinned by Task 3's test.

---

### Task 1: Android: bind the guest account in Debug builds

**Files:**
- Create: `core/shell/src/main/kotlin/com/whitelabel/core/shell/brand/DebugGuestKoin.kt`
- Create: `core/shell/src/debug/kotlin/com/whitelabel/core/shell/brand/BuildTypeModules.kt`
- Create: `core/shell/src/release/kotlin/com/whitelabel/core/shell/brand/BuildTypeModules.kt`
- Create: `core/shell/src/debug/res/values/debug_guest.xml`
- Modify: `core/shell/src/main/kotlin/com/whitelabel/core/shell/brand/ShellKoin.kt` (`foundationModules`, around line 75)
- Modify: `core/shell/src/main/kotlin/com/whitelabel/core/shell/FoundationApplication.kt:52`
- Modify: `core/domain/src/commonMain/kotlin/com/whitelabel/core/domain/DebugGuestAccount.kt` (KDoc only)
- Test: `core/shell/src/test/kotlin/com/whitelabel/core/shell/brand/ShellKoinTest.kt`

**Interfaces:**
- Produces: `internal fun debugGuestModule(email: String, password: String): Module`, which binds `single { DebugGuestAccount(email, password) }`.
- Produces: `internal fun buildTypeModules(context: Context): List<Module>`. The Debug version returns `listOf(debugGuestModule(context.getString(R.string.foundation_debug_guest_email), context.getString(R.string.foundation_debug_guest_password)))`. The Release version returns `emptyList()`.
- Produces: string resources `foundation_debug_guest_email` and `foundation_debug_guest_password` (Debug only, `translatable="false"`). Task 2 overrides them from the app.
- Produces: `foundationModules(definition, contributors, firebase = FirebaseWiring.OFF, buildTypeModules: List<Module> = emptyList())`, with `buildTypeModules` inserted after `firebaseModules(...)` and before `contributors.flatMap { it.modules() }`.

- [ ] **Step 1: Write the failing tests** in `ShellKoinTest`:

```kotlin
@Test fun `a debug guest module binds the guest account`() {
    val koin = koinApplication {
        modules(foundationModules(definition, emptyList(), buildTypeModules = listOf(debugGuestModule("me@x.test", "pw"))))
    }.koin
    assertThat(koin.get<DebugGuestAccount>()).isEqualTo(DebugGuestAccount("me@x.test", "pw"))
}

@Test fun `a linked contributor's guest account replaces the debug one`() {
    val koin = koinApplication {
        modules(foundationModules(definition, listOf(MockApiContributor()), buildTypeModules = listOf(debugGuestModule("me@x.test", "pw"))))
    }.koin
    assertThat(koin.get<DebugGuestAccount>()).isEqualTo(DebugGuestAccount(FakeData.GUEST_EMAIL, FakeData.PASSWORD))
}

@Test fun `without build type modules or the fake there is no guest account`() {
    val koin = koinApplication { modules(foundationModules(definition, emptyList())) }.koin
    assertThat(koin.getOrNull<DebugGuestAccount>()).isNull()
}
```

`FakeData` is in `:core:mockapi`, which this test already uses. If `GUEST_EMAIL` or `PASSWORD` aren't visible from here, assert the literal values from Global Constraints instead.

- [ ] **Step 2: Run the tests and check they fail**

Run: `./gradlew :core:shell:testDebugUnitTest --tests '*ShellKoinTest*'`
Expected: compilation fails, because `debugGuestModule` and the `buildTypeModules` parameter don't exist yet.

- [ ] **Step 3: Implement it.** Add `debugGuestModule`, the two `buildTypeModules` files and the `foundationModules` parameter. Write `debug_guest.xml` with the default account. In `FoundationApplication.startFoundation`, pass `buildTypeModules = buildTypeModules(this)`. Update `DebugGuestAccount`'s KDoc: Debug builds bind it from the shell (iOS: from Swift's `DebugControls`), the fake replaces it with its own, and Release never binds it. Extend the KDoc of `foundationModules` to mention the build type's modules, in that order.

- [ ] **Step 4: Run the tests and check they pass**

Run: `./gradlew :core:shell:testDebugUnitTest :core:shell:testReleaseUnitTest`
Expected: PASS. `the foundation graph resolves with only the platform's context` still passes.

- [ ] **Step 5: Commit**

```bash
git add core/shell core/domain
git commit -m "feat(auth): offer the debug guest sign-in on the real backend"
```

### Task 2: Android: let `local.properties` replace the guest account

**Files:**
- Create: `build-logic/convention/src/main/kotlin/com/whitelabel/buildlogic/apps/DebugGuestOverride.kt`
- Modify: `build-logic/convention/src/main/kotlin/com/whitelabel/buildlogic/AndroidApplicationConventionPlugin.kt` (load once, near `AppConfigLoader.load`; publish in `onVariants`)
- Test: `build-logic/convention/src/test/kotlin/com/whitelabel/buildlogic/apps/DebugGuestOverrideTest.kt`

**Interfaces:**
- Consumes: the resource names `foundation_debug_guest_email` and `foundation_debug_guest_password` from Task 1.
- Produces: `data class DebugGuestOverride(val email: String, val password: String)`.
- Produces: `fun debugGuestOverride(properties: Properties?, label: String): DebugGuestOverride?`. It returns null when both keys are absent or blank, and throws `InvalidAppConfigException(listOf("$label: debug.guest.email and debug.guest.password must be set together"))` when only one is set. `label` is the file's path relative to the root (`local.properties`).
- Produces: `fun debugGuestResValues(override: DebugGuestOverride?, buildType: String): Map<String, String>`, a map of resource name to `androidStringResource(value)`. It is empty unless `buildType == "debug"` and `override != null`.

- [ ] **Step 1: Write the failing tests.** In `DebugGuestOverrideTest`:
  - `no file or no keys means no override`: `debugGuestOverride(null, "local.properties")` and `debugGuestOverride(Properties(), …)` are both null.
  - `both keys give the override, trimmed`: `debug.guest.email= bob@shared.test ` and `debug.guest.password=Password123!` give `DebugGuestOverride("bob@shared.test", "Password123!")`.
  - `one key alone fails naming both`: email only, and then password only, each throw `InvalidAppConfigException` whose message contains `local.properties: debug.guest.email and debug.guest.password must be set together`.
  - `a blank key counts as absent`: email set with `debug.guest.password=   ` throws the same message, and both blank give null.
  - `only debug variants get the resources`: `debugGuestResValues(o, "release")` and `debugGuestResValues(null, "debug")` are empty. `debugGuestResValues(o, "debug").keys` equals `setOf("foundation_debug_guest_email", "foundation_debug_guest_password")`.
  - `the password is escaped as a string resource`: for the password `a'b"c\d@%s`, `debugGuestResValues(...)["foundation_debug_guest_password"]` equals `androidStringResource("a'b\"c\\d@%s")`.

- [ ] **Step 2: Run the tests and check they fail**

Run: `./gradlew -p build-logic :convention:test --tests '*DebugGuestOverrideTest*'`
Expected: compilation fails, because `debugGuestOverride` doesn't exist yet.

- [ ] **Step 3: Implement the two functions, then wire the plugin.** Read `rootDir/local.properties` if it is a file (UTF-8, as `AppConfigLoader` reads `app.properties`), and call `debugGuestOverride` once at configuration. In `onVariants`, put each `debugGuestResValues(override, buildType)` entry with `variant.resValues.put(variant.makeResValueKey("string", name), ResValue(value, null))`. An app resValue overrides the shell's Debug resource of the same name.

- [ ] **Step 4: Run the tests and check they pass, then check the wiring by hand**

Run: `./gradlew -p build-logic :convention:test`, which should PASS. Then add `debug.guest.email=bob@shared.test` and `debug.guest.password=Password123!` to `local.properties`, and run `./gradlew :apps:showcase:mergeDebugResources`. `apps/showcase/build/generated/res/resValues/debug/values/gradleResValues.xml` should contain both `foundation_debug_guest_*` entries with bob, and the release folder should have neither. Remove only the email line, then run it again: the build fails with the Step 1 message. Restore `local.properties` afterwards.

- [ ] **Step 5: Commit**

```bash
git add build-logic
git commit -m "feat(build): let local.properties pick the debug guest account"
```

### Task 3: iOS: bind the guest account in Debug builds

**Files:**
- Modify: `core/ios/src/iosMain/kotlin/com/whitelabel/core/ios/SharedGraph.kt` (state near `debugFake`, `start` around line 160)
- Modify: `ios/WhitelabelKit/Sources/WhitelabelKit/Shell/DebugControls.swift` (`beforeStart`)
- Test: `core/ios/src/iosTest/kotlin/com/whitelabel/core/ios/SharedEntryIosTest.kt` (or a new `SharedGraphDebugGuestTest.kt` beside it, following its setup and teardown)

**Interfaces:**
- Consumes: `DebugGuestAccount` (`:core:domain`, already exported through the framework).
- Produces: `fun useDebugGuest(email: String, password: String)` and `fun clearDebugGuest()` on `SharedGraph`. `start` adds `module { single { guest } }` after `firebaseModules(firebaseMode)` and before the contributors, only when a guest was set.

- [ ] **Step 1: Write the failing tests** (kotlin.test, using this test file's existing platform and definition helpers):
  - `a debug guest is bound on the real backend`: `useDebugGuest("me@x.test", "pw")`, then `start(..., useMockApi = false)`. Then `KoinPlatform.getKoin().get<DebugGuestAccount>()` equals `DebugGuestAccount("me@x.test", "pw")`.
  - `the fake's guest replaces the debug one`: the same with `IosMock.enable()` and `useMockApi = true` gives the fake's `alice@shared.test` / `Password123!`.
  - `no guest unless one is set`: after `clearDebugGuest()`, `start(..., useMockApi = false)` gives `getOrNull<DebugGuestAccount>() == null`.

  Each test calls `stop()` and `clearDebugGuest()` in its teardown.

- [ ] **Step 2: Run the tests and check they fail**

Run: `./gradlew :core:ios:iosSimulatorArm64Test`
Expected: compilation fails, because `useDebugGuest` doesn't exist yet.

- [ ] **Step 3: Implement it.** Add the state and the two functions, and add the module in `start`. In `DebugControls.beforeStart`, next to the `useFirebaseDebug` call, add `SharedGraph.shared.useDebugGuest(email: "alice@shared.test", password: "Password123!")`. The file is already wrapped in `#if DEBUG`.

- [ ] **Step 4: Run the tests and the iOS gate and check they pass**

Run: `./gradlew :core:ios:iosSimulatorArm64Test && scripts/check-ios.sh`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/ios ios
git commit -m "feat(ios): offer the debug guest sign-in on the real backend"
```

### Task 4: Docs and end-to-end checks

**Files:**
- Modify: `docs/api-environments.md` (the guest bullet under "Working without the backend", plus a line under "Local backend")
- Modify: `docs/ios.md` (the `-mockApi` row)

- [ ] **Step 1: Update the docs.** In `api-environments.md`, the button now appears in every Debug build. Against the local backend it signs in as `alice@shared.test` (run `npm run db:seed` first). `debug.guest.email` and `debug.guest.password` in `local.properties` replace that account on Android (both or neither), and on the fake the fake's alice is always used. In `ios.md`, drop "the sign-in screen then shows…" from the `-mockApi` row and add a sentence: Debug builds always show the button, as alice.

- [ ] **Step 2: Check that Release contains nothing**

Run: `./gradlew :apps:showcase:assembleRelease`, then check the APK under `apps/showcase/build/outputs/apk/release/`. `unzip -p <apk> 'classes*.dex' | grep -c 'Password123!'` should print `0`, and `$ANDROID_HOME/build-tools/<latest>/aapt2 dump resources <apk> | grep -c foundation_debug_guest` should print `0`.

- [ ] **Step 3: Run the gate**

Run: `scripts/check.sh`, which should PASS. Then `./gradlew :apps:showcase:connectedDebugAndroidTest` on an emulator, which should PASS (the E2E tests link the fake, so their guest is the fake's alice).

- [ ] **Step 4: Check it by hand on the real backend.** With `backend-api` running (`docker compose up -d postgres && npm run db:deploy && npm run db:seed && npm run dev`), install Showcase Debug with no mock flag. The sign-in screen should show **Continue as guest (debug)**, and tapping it should land on home as Alice. Do the same on the iOS simulator without `-mockApi`.

- [ ] **Step 5: Commit**

```bash
git add docs
git commit -m "docs(auth): say the debug guest works on every debug build"
```
