# Ads App Hooks Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let an app hide a declared ad placement at runtime (`AdsController.setHidden`) and end the ad-free window early (`AdFree.clear()`), on both platforms.

**Architecture:** Both live in `:core:ads:shared` (KMP). The hidden set is one more input to the flows `AdsController` already combines, and `adGate` gets one more rule. `clear()` writes an empty window and resets the in-memory end. iOS gets both through `SharedGraph`, with no Swift change.

**Tech Stack:** Kotlin Multiplatform, kotlinx.coroutines, Okio `FakeFileSystem`, kotlin.test + AssertK.

**Spec:** `docs/superpowers/specs/2026-11-13-ads-app-hooks-design.md`

## Global Constraints

- The hook can only hide. Consent, first launch, ad-free time and ads-off keep their meaning and order.
- A hidden placement never reaches `awaitTracking`, so no ad request and no tracking prompt.
- `setHidden` on an undeclared placement, or with ads off, is a no-op. It never throws.
- No app identity anywhere (foundation rule). Showcase needs no change.
- Commits: Conventional Commits, header ≤ 72 chars, Claude's `Co-Authored-By` trailer, branch `feat/ads-app-hooks`.

## Review Focus

1. **A stale stored value after `clear()`:** `AdFree`'s store collector publishes only growing ends, and a document read before the clear could still be in flight. A cleared window must stay cleared. → Task 2, `clearIsNotUndoneByTheStoredWindow`.
2. **`clear()` before the stored window has loaded** (called right at startup): it must not leave `loaded` false forever, and the old window must not come back when the load finishes. → Task 2, `clearBeforeLoadStaysCleared`.
3. **Un-hiding during an open tracking prompt, or hiding while one is open:** hiding mid-prompt must emit `Hidden(APP_HIDDEN)` at once (`transformLatest` cancels the wait), and un-hiding later must not ask a second time in the same launch. → Task 1, `hidingWhileTheTrackingPromptIsOpenHidesAndDoesNotAskTwice`.
4. **`setHidden` before anyone has asked for `bannerState(placement)`:** the first collector must already see it hidden. → Task 1, `hiddenBeforeTheFirstReadStartsHidden`.
5. **The same placement read from two places** (a test, or Android and Swift both holding it): one shared flow, both see the change. Covered by `bannerState`'s existing one-flow-per-placement rule. → Task 1, `setHiddenReachesEveryReaderOfThePlacement`.

---

### Task 1: `setHidden` and the `APP_HIDDEN` rule

**Files:**
- Modify: `core/ads/shared/src/commonMain/kotlin/com/whitelabel/core/ads/AdGate.kt`
- Modify: `core/ads/shared/src/commonMain/kotlin/com/whitelabel/core/ads/AdsController.kt`
- Test: `core/ads/shared/src/commonTest/kotlin/com/whitelabel/core/ads/AdGateTest.kt`, `AdsControllerTest.kt`

**Interfaces:**
- Produces:
  - `HideReason.APP_HIDDEN`, declared after `UNDECLARED` and before `LOADING` in the enum.
  - `adGate(adsEnabled, placementDeclared, hiddenByApp: Boolean, consent, launchNumber, adFreeUntil, now)`. The new parameter is third, and it's checked right after `UNDECLARED`.
  - `fun AdsController.setHidden(placement: String, hidden: Boolean)`

- [ ] **Step 1: Write the failing tests**
  - `AdGateTest`:
    - add `hiddenByApp: Boolean = false` to the `gate(...)` helper;
    - `app hidden hides` → `hide(APP_HIDDEN)`;
    - `ads off beats app hidden` → `ADS_OFF`;
    - `undeclared beats app hidden` → `UNDECLARED`;
    - `app hidden beats pending consent` (consent `Unknown`) → `APP_HIDDEN`.
  - `AdsControllerTest`:
    - `hiding a ready banner hides it and showing brings it back`: launch 2, granted, tracking finished → `ready`. Then `setHidden("home", true)` → `hidden(APP_HIDDEN)`, then `false` → `ready`.
    - `hiddenBeforeTheFirstReadStartsHidden`: `setHidden("home", true)` before `bannerState("home")` → `hidden(APP_HIDDEN)`, and `tracking.calls == 0`.
    - `a hidden placement never asks for tracking`: launch 2, hidden from the start → `tracking.calls == 0` after `runCurrent()`.
    - `hidingWhileTheTrackingPromptIsOpenHidesAndDoesNotAskTwice`: launch 2, the prompt opens (`calls == 1`, not finished). Hide → `hidden(APP_HIDDEN)`. Then `tracking.finish()`, un-hide → `ready`, `calls == 1`.
    - `setHidden on an undeclared placement changes nothing`: `setHidden("nope", true)` doesn't throw, and `bannerState("nope")` stays `hidden(UNDECLARED)`.
    - `setHiddenReachesEveryReaderOfThePlacement`: two `bannerState("home")` reads are the same instance, and both read `APP_HIDDEN` after hiding.

- [ ] **Step 2: Run them to make sure they fail**
  Run: `./gradlew :core:ads:shared:jvmTest`
  Expected: compile errors on `APP_HIDDEN` / `setHidden` / the `hiddenByApp` argument.

- [ ] **Step 3: Implement**
  - The new rule in `adGate`.
  - In `AdsController`: `private val hidden = MutableStateFlow<Set<String>>(emptySet())`. `setHidden` adds the placement to it or removes it. `decision()` passes `placement in hidden.value`. `createBanner`'s `combine` adds `hidden` as a fifth input.

- [ ] **Step 4: Run the tests to make sure they pass**
  Run: `./gradlew :core:ads:shared:jvmTest :core:ads:shared:iosSimulatorArm64Test`
  Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit** `feat(ads): let an app hide a placement`

---

### Task 2: `AdFree.clear()` and the docs

**Files:**
- Modify: `core/ads/shared/src/commonMain/kotlin/com/whitelabel/core/ads/AdFree.kt`
- Modify: `docs/ads.md`: a new `## App hooks` section after "Ad-free (rewards, purchases)", and a `clear()` line in that section's code block
- Test: `core/ads/shared/src/commonTest/kotlin/com/whitelabel/core/ads/AdFreeTest.kt`

**Interfaces:**
- Produces: `suspend fun AdFree.clear()`. Afterwards `state.value == Inactive`, `loaded.value == true`, and the stored `untilEpochMillis == null`.

- [ ] **Step 1: Write the failing tests**
  - `clear ends an active window`: `grantUntil(at(5))`, then `clear()` → `Inactive`.
  - `clear empties the store`: after `clear()`, a fresh `AdFree` on the same path loads `Inactive`.
  - `extend after clear starts from now`: `clear()`, then `extend(1.hours)` → `Active(at(1))`.
  - `clearIsNotUndoneByTheStoredWindow`: write a window to the store, then create `AdFree` and immediately `clear()` before running the scheduler, then `runCurrent()` → still `Inactive`.
  - `clearBeforeLoadStaysCleared`: same, and also `loaded.value == true`.

- [ ] **Step 2: Run them to make sure they fail**
  Run: `./gradlew :core:ads:shared:jvmTest`
  Expected: compile error on `clear`.

- [ ] **Step 3: Implement `clear()`**
  - `store.update { it.copy(untilEpochMillis = null) }`, then `until.value = null`, then `isLoaded.value = true`.
  - Guard the collector: a private `cleared` generation, or comparing the document read, so that a document read before the clear is never published afterwards. `publish`'s "only grow" rule stays as it is for `grantUntil` and `extend`.
  - KDoc: "Ends the window now. Unlike [grantUntil] and [extend], this shortens."

- [ ] **Step 4: Run the tests to make sure they pass**
  Run: `./gradlew :core:ads:shared:jvmTest :core:ads:shared:iosSimulatorArm64Test`
  Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Docs.** `## App hooks` in `docs/ads.md`:
  - `setHidden` (only hides; idempotent; lasts for the process; Kotlin `get<AdsController>().setHidden("home", true)`; Swift `SharedGraph.shared.adsController()?.setHidden(placement:hidden:)`);
  - `clear()` (Kotlin and Swift);
  - add `APP_HIDDEN` to the "House rules" list as rule 3 ("the app hid the placement"), renumbering the rest.

- [ ] **Step 6: Run the gate**
  Run: `scripts/check.sh`
  Expected: it passes.

- [ ] **Step 7: Commit** `feat(ads): let an app end ad-free time`
