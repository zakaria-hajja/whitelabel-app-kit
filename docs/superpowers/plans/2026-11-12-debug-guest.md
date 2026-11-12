# Debug Guest Sign-In Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** In Debug builds that run on the in-memory fake backend, the sign-in screen shows a
"Continue as guest (debug)" button. One tap signs in as a seeded test account, so every tab of the
app can be reached without typing credentials or running a server.

**Architecture:**
- The fake backend (`:core:mockapi`, linked only into Debug builds) binds a `DebugGuestAccount`.
- `LoginViewModel` takes it as an optional dependency and exposes `guestAvailable`; a
  `OnContinueAsGuest` action signs in with its credentials through the normal session flow.
- Both platforms' sign-in screens show the button only when `guestAvailable` is true. Release builds
  never link the fake backend, so the button can't exist there.

**Tech Stack:** Kotlin KMP, Koin, Compose (Android), SwiftUI (iOS), `kotlin.test` + AssertK.

**Spec:** the owner's decision of 2026-11-12, made in conversation. Copied here because there's no
separate design doc:
- A Debug-only shortcut, not a product guest mode.
- It signs in to the mock backend as a test user, so every tab works.
- It never appears in release builds.

## Global Constraints

- The button appears only when the fake backend is linked:
  - Android: a Debug build with `-Pwhitelabel.mockApi=true`;
  - iOS: a Debug build launched with `-mockApi`.

  Release builds never contain it; `scripts/verify-apps.sh` already fails a release dex that
  contains `:core:mockapi`.
- Shared features never depend on `:core:mockapi`. The account type lives in `:core:domain`, which
  both already use.
- Button copy: en "Continue as guest (debug)", fr "Continuer en invité (debug)". Test tag and
  accessibility identifier: `auth.guest`.
- The guest account is the fake's first seeded user: `alice@shared.test` with `FakeData.PASSWORD`.
- UI tests are paused by the owner. Don't write or run XCUITests or Android instrumented tests;
  unit tests use TDD.
- Git rules as in `CLAUDE.md`. Stage files by explicit path.

## Review Focus

1. The button is tapped while a sign-in is already running. Expected: ignored, like a second submit;
   no double sign-in (Task 1).
2. The guest sign-in fails, for example because the fake is offline (`<scheme>://_fake/offline`).
   Expected: the normal sign-in error message, and the button usable again (Task 1).
3. No guest account is bound (a real backend, or release). Expected: `guestAvailable` is false and the
   action is a no-op (Task 1).
4. Text typed into the form before tapping guest. Expected: the guest credentials are used, and the
   typed email is not overwritten in saved state (Task 1).

---

### Task 1: Guest account, ViewModel action and wiring

**Files:**
- Create: `core/domain/src/commonMain/kotlin/com/whitelabel/core/domain/DebugGuestAccount.kt`
- Modify: `feature/authentication/shared/src/commonMain/kotlin/com/whitelabel/feature/authentication/presentation/login/LoginViewModel.kt`
- Modify: `feature/authentication/shared/src/commonMain/kotlin/com/whitelabel/feature/authentication/AuthenticationKoin.kt`
- Modify: `core/mockapi/src/commonMain/kotlin/com/whitelabel/core/mockapi/MockApiKoin.kt` (+ `core/mockapi/build.gradle.kts`: `implementation(project(":core:domain"))` if it isn't already reachable)
- Modify: `core/mockapi/src/commonMain/kotlin/com/whitelabel/core/mockapi/FakeData.kt` (add `const val GUEST_EMAIL = "alice@shared.test"` and use it for the seeded user)
- Test: `feature/authentication/shared/src/commonTest/kotlin/com/whitelabel/feature/authentication/presentation/AuthViewModelsTest.kt`, a mockapi test (the module's existing Koin or FakeBackend test file)

**Interfaces:**
- Produces:
  ```kotlin
  // :core:domain
  /** A ready-made account for Debug builds on the fake backend; bound only by :core:mockapi. */
  data class DebugGuestAccount(val email: String, val password: String)

  // LoginViewModel
  class LoginViewModel(session: SessionRepository, analytics: Analytics, savedState: SavedStateHandle,
                       guest: DebugGuestAccount? = null)
  data class LoginState(..., val guestAvailable: Boolean = false)
  sealed interface LoginAction { ...; data object OnContinueAsGuest : LoginAction }
  ```
- Koin: `authenticationModule` builds `LoginViewModel` with `getOrNull<DebugGuestAccount>()`. Replace
  `viewModelOf(::LoginViewModel)` with an explicit `viewModel { LoginViewModel(get(), get(), get(), getOrNull()) }`
  if `viewModelOf` doesn't resolve a nullable parameter as optional. Check, and keep whichever works.
  `mockApiModule` adds `single { DebugGuestAccount(FakeData.GUEST_EMAIL, FakeData.PASSWORD) }`.
- iOS: `SharedGraph.loginViewModel(email:)` resolves through Koin, so it gets the account when
  `IosMock.enable()` registered the fake. Check that; no Swift-facing API change should be needed.

- [ ] **Step 1: Write the failing tests** in `AuthViewModelsTest`:
  - `` `guest is unavailable without a debug account` ``: `LoginViewModel(..., guest = null)`.
    Assert `state.guestAvailable` is false, and that `onAction(OnContinueAsGuest)` doesn't call the
    session.
  - `` `continue as guest signs in with the debug account` ``: with
    `DebugGuestAccount("alice@shared.test", "Password123!")`, assert `guestAvailable` is true, and
    after the action the fake session received `signIn("alice@shared.test", "Password123!")` and
    analytics got `login` with `method=debug_guest`.
  - `` `guest sign-in ignored while submitting` `` (Review Focus 1): start a guest sign-in that
    suspends, tap again, and assert one `signIn` call.
  - `` `guest sign-in failure shows the error and re-enables` `` (Review Focus 2): the session fails,
    so `state.error` is the mapped error and `isSubmitting` is false.
  - `` `guest sign-in leaves the typed email alone` `` (Review Focus 4): type `"me@x.com"`, continue
    as guest, and assert `state.email == "me@x.com"` and the saved state still holds `"me@x.com"`.

  Then a mockapi test, `` `the fake backend offers its seeded guest account` ``: resolve
  `DebugGuestAccount` from `mockApiModule` and assert the email is `FakeData.GUEST_EMAIL`, that
  `FakeBackend` has that user, and that signing in with it succeeds.
- [ ] **Step 2: Run them.** `./gradlew :feature:authentication:shared:allTests :core:mockapi:allTests`.
  Expected: FAIL (unresolved `DebugGuestAccount` / `OnContinueAsGuest`).
- [ ] **Step 3: Implement.** The guest sign-in reuses `submit`'s flow (`isSubmitting` guard, error
  mapping) with the account's credentials, without touching `email`/`password` state.
- [ ] **Step 4: Run** the same command, plus `./gradlew :core:ios:iosSimulatorArm64Test`. Expected: PASS.
- [ ] **Step 5: Commit.** `feat(auth): add a debug guest sign-in on the fake backend`

### Task 2: The button on both platforms, and docs

**Files:**
- Modify: `feature/authentication/src/main/kotlin/com/whitelabel/feature/authentication/presentation/login/LoginScreen.kt`
- Modify: `feature/authentication/src/main/res/values/strings.xml`, `values-fr/strings.xml` (`auth_continue_as_guest`)
- Modify: `ios/WhitelabelKit/Sources/WhitelabelKit/Features/Authentication/LoginScreen.swift`
- Modify: `ios/WhitelabelKit/Resources/WhitelabelKit.xcstrings` ("Continue as guest (debug)", fr)
- Modify: `docs/api-environments.md` ("Working without the backend"), `docs/ios.md` (debug controls table)

**Interfaces:**
- Consumes: `LoginState.guestAvailable` and `LoginAction.OnContinueAsGuest` (Task 1).

- [ ] **Step 1: Android.** In `LoginScreen`, below "Create an account", add an `AppTextButton` with
  the guest string. It's shown only when `state.guestAvailable`, sends `OnContinueAsGuest`, has
  `Modifier.testTag("auth.guest")`, and is disabled while `isSubmitting`. Add a preview with
  `guestAvailable = true`.
- [ ] **Step 2: iOS.** In `LoginForm`, the same `Button(KitStrings.text("Continue as guest (debug)"))`
  with `TextButtonStyle()`. It's shown only when `state.guestAvailable`, sends
  `LoginActionOnContinueAsGuest.shared`, has `.accessibilityIdentifier("auth.guest")`, and is disabled
  while `isSubmitting`.
- [ ] **Step 3: Docs.** One line in each doc, giving where the button appears and that it signs in as
  `alice@shared.test`:
  - Android needs `-Pwhitelabel.mockApi=true` (or `whitelabel.mockApi=true` in `gradle.properties`);
  - iOS needs Debug and `-mockApi`.
- [ ] **Step 4: Verify.**
  - `scripts/check.sh`.
  - `./gradlew -Pwhitelabel.mockApi=true :apps:showcase:assembleDebug :apps:showcase:assembleRelease && scripts/verify-apps.sh`:
    all OK, with release reporting "no mock backend".
  - `WL_IOS_SIMULATOR="iPhone 18 Pro Max" scripts/check-ios.sh`, which skips UI tests.
  - A smoke check, not a test, per platform:
    - **Android:** install the mock-backend Debug build on an emulator no other session uses and
      launch it. With `adb shell uiautomator dump`, find the node with resource-id or text
      "Continue as guest (debug)" and tap its bounds with `adb shell input tap x y`. Then screenshot
      and confirm the Home tab with the "Alice" greeting.
    - **iOS:** launch with `-mockApi -freshInstall` and screenshot the sign-in screen showing the
      button. Tapping isn't possible without UI automation, so say so.
- [ ] **Step 5: Commit.** `feat(auth): show the debug guest button on the sign-in screen`, then
  `docs: document the debug guest sign-in` if the docs go in a separate commit.
