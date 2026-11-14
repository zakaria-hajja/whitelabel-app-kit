# Content gate: design and plan (foundation, row 7b)

_Architect, 2026-11-14. Card: https://trello.com/c/rQTcDKFu. Foundation pinned at `10ecc5c`
(Munch is pinned at the same commit). Read-only design; no code changed._

## Revision 2 (2026-11-14): answers to the design review

Review: `content-gate-design-review.md` (CHANGES REQUESTED). I checked every finding against
foundation `10ecc5c` and Munch `3cdd28b`.

| # | Finding | Verdict | What changed |
|---|---|---|---|
| B1 | Munch's path isn't today's | **Valid.** Rev 1 sent every app through `ShellRoot`, a hoisted `NavController` and a new `shouldGatherConsent` signature. | One branch at the top: `contentGate == null` runs today's code unchanged (§3.2, §3.3). `shouldGatherConsent` keeps its signature and truth table. New tests prove the no-gate branch is taken: test 9 (Showcase) and the unchanged consent tests (Task 4). One clarification: "byte-for-byte" means the same composables, arguments, effects, splash rule and consent rule. Compose group keys come from source positions, so editing the file shifts them anyway. That's harmless: saved instance state never survives an app update. |
| B2 | Only Showcase stands in for Munch | **Valid.** Munch has ads under tabs, rewards, reminders, and a trailing-closure `AppDefinition(… ads: ads) { … }` (`MunchApp.swift:63,69`). | New Task 7: run Munch's `scripts/check.sh`, all four `connectedDebugAndroidTest` modules and `scripts/check-ios.sh` with `foundation/` at the branch head, locally, before the PR. Counts go in the PR. |
| B3 | Test 7 contradicts `ShellRoot` | **Valid.** A plain `LaunchedEffect` fires on every entry into CONTENT. | Took the recommended option. `onContentShown` fires on **each** entry into CONTENT and never in LOCK. Dedupe stays with `consentRequested` plus `Consent.started`. Test 7 asserts 0 → 1 → 2. |
| B4 | The iOS mirror lags `onAppForeground`; `scenePhase` is per scene | **Valid.** | Lifecycle moves to `UIApplication.willEnterForegroundNotification` / `didEnterBackgroundNotification`, observed by a new `ContentGateDriver` that `AppRuntime` owns, not a view. The driver re-reads `gate.state.value` synchronously right after each call. A unit test posts the notification and sees `.lock` with no `await`. `LifecycleSignal` and `scenePhase` are dropped. |
| N1 | Every unlock logs a `screen_view` | **Valid** (`ShellNavHost.kt:115-120`; the listener fires on add). | Kept as intended behavior (the screen is viewed again; screen name only). §4 and R1 corrected. Test 5 asserts exactly one more `screen_view`. |
| N2 | Test 2's rig doesn't exist | **Valid that it doesn't exist; I pushed back on building it.** `BannerState.Ready` needs a granted `Consent`, a `LaunchCounter` past the first launch, `AdFree`, `TrackingPort` and `Clock`: a large rig for a property that is structural. | Test 2 now counts compositions of the whole content slot (a `SideEffect` counter). 0 while locked means nothing inside it (banner, ATT, screens) exists. Real banners: Munch's `RewardsFlowTest`/`TabsTest` (no gate, Task 7) and Tend's row 8 E2E (gated). |
| N3 | Windows outside the composition survive a relock | Valid | Rule added to the `contentGate` KDoc and the docs (R9). |
| N4 | Leaving for another app's activity relocks | Valid; Tend's concern | Listed for the row 8 card (§5, Tend E2E). |
| N5 | `addObserver` is main-thread only; `getOrNull` builds the gate in `Application.onCreate` | Valid | Registration posts to the main looper when off it (Task 3). The KDoc says the binding must be cheap and non-throwing, and that Android calls `onAppForeground` once at launch. |
| N6 | API details | Valid, all four | Swift calls `ContentGateKt.*`. `shellScreen` takes a **non-null** `ContentGateState` (the no-gate path never calls it), so there's no nullable-enum bridging. Swift `func contentGateProblem(bindsGate:)`. The KDoc says the gate never returns to PENDING. |
| N7 | No test of the no-gate branch beyond Showcase | Valid | Test 9 + Task 7 + the unchanged `FirebaseWiringTest` consent assertions. |

Also new in rev 2:
- The Android variable is `contentGate`, because `hasGate`/`gate` already mean the **sign-in** gate in
  `FoundationActivity` and `ShellNavHost`.
- The `onAppForeground` contract now covers both platforms. Android calls it once at launch
  (process `ON_START`). iOS doesn't (no `willEnterForeground` on a cold launch). The gate must give
  the same answer either way.
- ADR 0007 §8 and R27 name `scenePhase` as the iOS signal. The Lead should amend them to the
  `UIApplication` notifications (R6).

## 1. Recommendation (one paragraph)

Add an optional **`ContentGate`** to the foundation, defined once in shared Kotlin
(`:core:shell:shared`), bound by the app in Koin. The app's definition declares a **lock screen**
(Compose on Android, SwiftUI on iOS). **When no gate is declared or bound, the shell runs today's code
unchanged.** When there is a gate, the shell picks one of three root screens: **splash**, **lock** or
**content**. The lock **replaces** the shell content in composition, so it never draws over it.
Everything we must hold is already composed inside the shell content, so it waits by itself:
- the banner and ATT (ATT fires only when a banner would load),
- the pending deep link (it already waits in `ShellViewModel.pendingDeepLink`),
- the launch UMP form (on the gate path, its trigger waits for the content screen),
- every dialog and sheet a screen opened.

The foundation reports app foreground and background to the gate at process/app level: Android through
`ProcessLifecycleOwner`, iOS through `UIApplication` notifications. The app's shared code decides when
to lock. The foundation also applies recents protection on Android whenever the gate asks for it.

Munch binds nothing and declares nothing, so it takes the unchanged branch. iOS **does** need a
small WhitelabelKit counterpart. That corrects ADR 0007's first version: wrapping
`RootView` from Tend can't stop `TabsView`'s `.task { gatherConsent() }`, the banner, or pending
links from running under an overlay.

## 2. What exists today (read at `10ecc5c`)

- `FoundationActivity.setContent` shows `ShellNavHost` when `state.showsContent(hasGate)`. `hasGate` is
  the **sign-in** gate (`definition.gateEntry`). Nothing else is drawn: there's no splash composable,
  because the system splash is held by `setKeepOnScreenCondition`.
- Consent: the splash keep-condition posts `consent.gatherOnLaunch()` the first time the splash lets
  go (`shouldGatherConsent(needsConsent, splashKept, alreadyRequested)`). `Consent` gathers once per
  instance (`started.compareAndSet`). UMP needs a *resumed* activity.
- Deep links: `takeDeepLink` moves the link into `ShellViewModel.pendingDeepLink` (SavedStateHandle).
  Only `ShellNavHost`'s `LaunchedEffect(signedIn, deepLink)` (line 180) opens it.
- Screen views: `ShellNavHost`'s `DisposableEffect(navController)` adds an `OnDestinationChangedListener`,
  which fires at once with the current destination (lines 115-120).
- Banners: only composed (`ShellNavHost.TabBanner`, the app's `AdBanner(name)`). Nothing preloads.
- `ShellNavHost(…, navController: NavHostController = rememberNavController(), …)`. The activity
  doesn't pass one.
- `FoundationApplication.startFoundation()` runs `startKoin`. Tests call it again after `stopFoundation()`.
- iOS `RootView` → `ShellContent`: splash / `GateView` / `TabsView`. `.task { runtime.gatherConsent() }`
  sits on the gate and tabs views. Links go through `processPendingLink` on `onAppear` and on `linkTrigger`
  changes. `AppRuntime` (one per process) owns `Navigator` and the `SharedModel` of the shell.
  `SharedModel` seeds from `flow.value`, then follows a `for await` task.
- `androidx.lifecycle:lifecycle-process` 2.9.4 is already on the runtime classpath (appcompat → emoji2).
  We'll declare it explicitly.

## 3. API

### 3.1 Shared (`:core:shell:shared`, `commonMain`, `ContentGate.kt`, package `com.whitelabel.core.shell.model`)

```kotlin
/** What the app's lock says right now. */
enum class ContentGateState { PENDING, LOCKED, OPEN }

/**
 * An app-level gate in front of the whole app (an app lock). The app binds one in Koin; the shell
 * reads [state], reports the app's foreground/background, and applies [protectsAppSwitcher].
 * The shell never unlocks: the app's lock screen does that through its own implementation.
 *
 * Resolved from Koin while the app starts (Android: `Application.onCreate`), so creating it must
 * be cheap and must not throw.
 */
interface ContentGate {
    /**
     * PENDING until the app knows (e.g. its lock setting is loading): the splash stays. It must leave
     * PENDING quickly and **never return to it**. It is read on the main thread before each splash frame.
     */
    val state: StateFlow<ContentGateState>

    /** Hide the app's snapshot in the app switcher: true while PENDING and whenever the lock is on. */
    val protectsAppSwitcher: StateFlow<Boolean>

    /**
     * The app returned to the foreground (process level). Update [state] synchronously in this call
     * (no suspend, no dispatch), so the first frame after returning is already the lock.
     * Android also calls it once at launch, and iOS doesn't: the result must not depend on that.
     */
    fun onAppForeground()

    /** The app went to the background (process level; not on rotation, not for one iPad scene). */
    fun onAppBackground()
}

/** The gated shell's root screen. */
enum class ShellScreen { SPLASH, LOCK, CONTENT }

/** With a gate: SPLASH until the shell shows content and the gate isn't PENDING; then LOCK or CONTENT. */
fun shellScreen(showsContent: Boolean, gate: ContentGateState): ShellScreen

/** Why the app's lock wiring is wrong, or null: a lock screen without a bound gate, or the reverse. */
fun contentGateProblem(declaresLockScreen: Boolean, bindsGate: Boolean): String?
// messages: "contentGate() needs a ContentGate bound in the app's modules"
//           "a ContentGate is bound but the app definition doesn't call contentGate()"
```

Why a tri-state and not the card's `isLocked: StateFlow<Boolean>`: Tend's lock setting loads
asynchronously. A boolean would have to start `false`, so the first frame would show content. PENDING
keeps the splash up instead, so it fails safe. Swift sees `ContentGateKt.shellScreen(...)` and
`ContentGateKt.contentGateProblem(...)`. There's no "no gate" row: without a gate, nothing calls `shellScreen`.

### 3.2 Android (`:core:shell`)

```kotlin
// AppDefinitionBuilder
/**
 * Puts [lockScreen] in front of the whole app while the app's [ContentGate] (bound in its modules)
 * is LOCKED. The lock replaces the app's screens: nothing under it is composed. Only
 * `rememberSaveable` state and ViewModels survive a relock (plain `remember` doesn't). Windows outside
 * the composition (View dialogs, `PopupWindow`, toasts, the system share sheet) are NOT hidden:
 * never put protected content in them.
 */
fun contentGate(lockScreen: @Composable () -> Unit)

// AppDefinition
internal val lockScreen: (@Composable () -> Unit)? = null
```

**FoundationActivity: one branch, today's code on one side.**

```kotlin
private val contentGate: ContentGate? by lazy { getKoin().getOrNull() }

// onCreate, splash:
splash.setKeepOnScreenCondition {
    val gate = contentGate
    val keep: Boolean
    val splashKept: Boolean           // what shouldGatherConsent sees
    if (gate == null) {               // today, unchanged
        keep = !viewModel.state.value.showsContent(hasGate); splashKept = keep
    } else {
        val screen = shellScreen(viewModel.state.value.showsContent(hasGate), gate.state.value)
        keep = screen == SPLASH; splashKept = screen != CONTENT   // LOCK lets the splash go but holds consent
    }
    if (shouldGatherConsent(definition.needsConsent, splashKept, consentRequested)) requestConsent()
    keep
}
// setContent, inside AppTheme:
if (contentGate == null) {
    if (state.showsContent(hasGate)) ShellNavHost(/* today's arguments, no navController */)
} else {
    GatedShell(contentGate, state, deepLink, …)  // private composable in FoundationActivity.kt
}
```

The implementer writes the no-gate side as today's lines verbatim (only indented). The reviewer checks
that with `git diff -w`.

- `requestConsent()` = `consentRequested = true; window.decorView.post { consent.gatherOnLaunch() }`,
  extracted from today's body. `shouldGatherConsent` keeps its signature and body.
- `GatedShell` (gate path only):
  - `val gateState by gate.state.collectAsState()` (**not** `…WithLifecycle`, so a lock set while
    stopped is already composed on return);
  - `val nav = rememberNavController()`, hoisted so the back stack lives in memory across a relock;
  - `ShellRoot(shellScreen(state.showsContent(hasGate), gateState), definition.lockScreen!!, onContentShown = ::requestConsentIfDue) { ShellNavHost(…today's args…, navController = nav) }`.
  - `requestConsentIfDue()` = `if (shouldGatherConsent(definition.needsConsent, false, consentRequested)) requestConsent()`.
    It covers "unlocked after a cold start": by then the splash is gone and its condition isn't polled.
    The activity is resumed when the user unlocks, so UMP can show.
- `onCreate`, gate path only, before the first frame:
  `lifecycleScope.launch { gate.protectsAppSwitcher.collect { applyRecentsProtection(recentsProtection(SDK_INT, it)) } }`.
  It collects for the whole activity lifetime, so the flag is set before `onPause` takes the snapshot.

**`ShellRoot`** (`internal`, new file):
`ShellRoot(screen: ShellScreen, lockScreen: @Composable () -> Unit, onContentShown: () -> Unit, content: @Composable () -> Unit)`
- Root: `Box(Modifier.fillMaxSize().testTag(CONTENT_GATE_ROOT_TAG))`, with `rememberSaveableStateHolder()`
  above the `when`;
- SPLASH → nothing (the system splash covers it; the gate never goes back to PENDING, so this is
  only the cold start);
- LOCK → `Box(Modifier.fillMaxSize().testTag(LOCK_SCREEN_TAG)) { lockScreen() }`;
- CONTENT → `holder.SaveableStateProvider(SHELL_STATE_KEY) { content() }` +
  `LaunchedEffect(Unit) { onContentShown() }`. It fires on **every** entry into CONTENT, and the
  caller dedupes.

**Lifecycle and startup** (`FoundationApplication.startFoundation()`, after `startKoin`):
`val gate = GlobalContext.get().getOrNull<ContentGate>()`, then `checkContentGate(definition, gate)`.
If there's a gate, `registerContentGateLifecycle(gate)` adds `ContentGateLifecycle(gate)` to
`ProcessLifecycleOwner`: directly on the main thread, otherwise posted to the main looper (tests call
`startFoundation()` from the instrumentation thread). `stopFoundation()` removes it the same way.
`ContentGateLifecycle : DefaultLifecycleObserver`: `onStart` → `onAppForeground()`, `onStop` →
`onAppBackground()`. Why process level: `ON_STOP` already ignores rotation (700 ms debounce), and a
browser link can start a second `FoundationActivity` in another task. Process `ON_START` fires before
`onResume` and the first draw. Adding the observer while the process is STARTED dispatches `ON_START`
at once, which is the "once at launch" call in the contract.

**Recents:** `internal enum class RecentsProtection { NONE, NO_SCREENSHOT, FLAG_SECURE }`,
`internal fun recentsProtection(sdkInt: Int, on: Boolean)`: `on && sdk >= 33` → NO_SCREENSHOT, `on`
→ FLAG_SECURE, else NONE. `Activity.applyRecentsProtection(p)` calls `setRecentsScreenshotEnabled(p != NO_SCREENSHOT)`
on 33+ and sets or clears `FLAG_SECURE`.

### 3.3 iOS (WhitelabelKit + `core/ios`)

- `SharedGraph.contentGateOrNull(): ContentGate? = koin.getOrNull()`.
- `AppDefinition.init(style:startTab:modules:services:ads:lockScreen:_ features:)`, with
  `lockScreen: (@MainActor () -> AnyView)? = nil` placed after `ads`, before the feature builder.
  Munch's `AppDefinition(…, ads: ads) { … }` still binds the trailing closure to `features`: the forward
  scan (SE-0286) skips a defaulted parameter when a later one needs an argument. Task 5 pins it with a
  compile-level test, and Task 7 builds Munch.
- `func contentGateProblem(bindsGate: Bool) -> String?` on `AppDefinition` calls
  `ContentGateKt.contentGateProblem(declaresLockScreen: lockScreen != nil, bindsGate:)`. `AppRuntime.launch`
  calls `fatalError("Invalid app definition: …")` on a problem, as for `serviceProblem`.
- **`ContentGateDriver`** (new, `@MainActor @Observable final class`, owned by `AppRuntime`, nil when no gate):
  ```swift
  init(read: @escaping () -> ContentGateState, foreground: @escaping () -> Void,
       background: @escaping () -> Void, center: NotificationCenter = .default)
  private(set) var state: ContentGateState   // seeded from read()
  func refresh()                             // state = read(); called by AppRuntime's `for await` loop
  func screen(showsContent: Bool) -> ShellScreen  // ContentGateKt.shellScreen(showsContent:gate: state)
  ```
  It observes `UIApplication.willEnterForegroundNotification` → `foreground(); state = read()` and
  `didEnterBackgroundNotification` → `background(); state = read()` with `queue: nil`, so the handler
  runs synchronously in `post`, on the main thread. These are app-level: posted when the first scene
  enters the foreground and when the last one leaves, before the scene's first active frame. No post
  on a cold launch, which the contract covers. `AppRuntime` builds it with closures over
  `gate.state.value`, `gate.onAppForeground()` and `gate.onAppBackground()`, then starts
  `for await _ in gate.state { driver.refresh() }` for changes the gate makes on its own (unlock,
  the PENDING → resolved read). Closures keep the driver testable without implementing the Kotlin
  protocol in Swift. It's its own type because `SharedModel<State: AnyObject>` can't hold the
  SKIE-bridged enum.
- `ShellContent.content`: `if runtime.gateDriver == nil` → today's `content` builder, unchanged.
  Otherwise `GatedContent`:
  - `switch driver.screen(showsContent:)`: `.splash` → `SplashView`; `.lock` →
    `definition.lockScreen?() ?? AnyView(SplashView())` (never falls through to content); `.content`
    → today's gate/tabs builder, so `.task { gatherConsent() }` runs only on content, on each entry;
    `gatherOnLaunch` is once per instance.
  - `.onChange(of: driver.screen(...)) { runtime.processPendingLink() }` re-checks the pending link on unlock.
- `AppRuntime.processPendingLink`: `showsContent` becomes `gateDriver.map { $0.screen(showsContent: s) == .content } ?? s`,
  where `s` is today's expression. Without a gate it's exactly today's value.
- **Tend-only on iOS (Q2, Lead decision):** the app-switcher privacy cover. The kit ignores
  `protectsAppSwitcher`.

### 3.4 Tend's opt-in (row 8, not this branch)

- Android `tendDefinition`: `contentGate { TendLockRoot() }` plus `single<ContentGate> { get<TendLock>() }`.
- iOS `TendDefinition.make()`: `lockScreen: { AnyView(LockView()) }`.
- `TendLock` (`:tend:lock:shared`): starts PENDING and resolves once from one local read.
  `protectsAppSwitcher` is true while PENDING, then equals "lock on". `onAppBackground` records a
  monotonic timestamp and locks at once when grace is 0. `onAppForeground` locks synchronously past
  `tend_lock_grace_seconds`. It's safe to call at launch (Android) or not (iOS). `unlock()` comes from
  Tend's lock ViewModel.

## 4. Lifecycle and ordering

| Case | What happens |
|---|---|
| **No gate (Munch, Showcase, Tend before row 8)** | `contentGate == null`: today's splash rule, consent trigger, `ShellNavHost` call and iOS `content`. No observer, no window flags, no driver. |
| **Cold start, lock on** | The splash holds while `ShellViewModel` isn't ready **or** the gate is PENDING (Q1, Lead decision: the lock resolves before the splash ends). Then LOCK: no `ShellNavHost`, banner, link processing or UMP (`splashKept = screen != CONTENT`). Unlock → CONTENT → `ShellNavHost` opens any pending link → `onContentShown` → UMP. **Lock first, then consent.** |
| **Warm resume, Android** | Process `ON_START` → `onAppForeground()` decides synchronously → `collectAsState` has LOCKED before the first frame. Recents protection blanks the transition snapshot. |
| **Warm resume, iOS** | `willEnterForeground` → `foreground()` then `state = read()` in the same call → SwiftUI renders `.lock` in the first frame. Tend's `.inactive` cover handles the snapshot. |
| **Relock while using the app** | CONTENT → LOCK removes `ShellNavHost` with its dialogs and sheets. The hoisted controller keeps the back stack, the `SaveableStateHolder` keeps `rememberSaveable` state, and ViewModels stay. **On unlock, one `screen_view` is logged** for the current destination (the listener is re-added). That's intended: the screen is viewed again, and it carries the screen name only. |
| **Process death** | New process → PENDING → splash → LOCKED. The saved hoisted controller, the holder's map and the pending link are restored. Unlock → the same destination. |
| **Notification tap while locked** | The link waits in `pendingDeepLink` (`ShellNavHost` isn't composed; on iOS `processPendingLink` sees `screen != .content`). Unlock → it opens. |
| **UMP** | The launch form only after the first unlock. Accepted gap R4: a form *open* during a relock stays above the lock. |
| **Ads banner / ATT** | Composed only inside content, so never requested while locked or pending. |
| **a11y tree** | LOCK composes only the lock screen. |
| **Recents** | API 33+ `setRecentsScreenshotEnabled(false)`, 26–32 `FLAG_SECURE`, from `onCreate`. iOS: Tend's cover. |
| **Sign-in gate and content gate** | Independent. The lock sits in front of the whole `ShellNavHost`. `GateTest` stays green. |
| **Leaving for another app (share sheet, SAF, browser)** | Process `ON_STOP` → Tend may relock (grace 0). Tend's concern for row 8 (N4). |

## 5. Tests

**Shared, `commonTest` (`ContentGateTest.kt`)**: PENDING → SPLASH even when ready; LOCKED/OPEN while not
ready → SPLASH; LOCKED → LOCK; OPEN → CONTENT; `contentGateProblem` for all four combinations.

**Android JVM unit (`core/shell/src/test`)**:
- `recentsProtection`: 33/32/26 with on, and off → NONE.
- `ContentGateLifecycle`: a `LifecycleRegistry.createUnsafe` owner, START/STOP forwarded once each.
- `AppDefinitionTest`: `contentGate {}` sets `lockScreen`; the default is null.
- `checkContentGate`: three cases.
- `FirebaseWiringTest`'s `shouldGatherConsent` assertions stay **unmodified**, which proves the truth table didn't move.

**Android instrumented (`core/shell/src/androidTest/.../ContentGateTest.kt`)**: `ShellRoot` around the real
`ShellNavHost` with a hoisted controller, driven the way `GateTest.show()` drives `ShellNavHost`:
1. `locked_composesOnlyTheLockScreen`: the lock tag exists. The unmerged tree has no "Home screen",
   no `BOTTOM_BAR_TAG` and no tab labels.
2. `locked_composesNothingOfTheContent`: a `SideEffect { contentCompositions++ }` at the top of the
   content slot. It's 0 while SPLASH and LOCK, and > 0 after unlock, so the test can fail. Banners,
   ATT and screens all live in that slot.
3. `deepLinkWhileLocked_opensOnlyAfterUnlock`: "Other screen" is absent and `consumed == 0` while
   locked. After unlock, "Other screen" shows and `consumed == 1`.
4. `relock_removesOpenDialog`: a Compose `Dialog` in content, relock → its text doesn't exist.
5. `unlock_returnsToSameDestinationWithState`: tab two, "Taps 2" → lock → unlock → same screen,
   "Taps 2", same `previousBackStackEntry`, and `screenViews` grew by exactly 1 with the same screen name.
6. `processDeathWhileLocked_restoresDestinationAfterUnlock`: `StateRestorationTester`, controller hoisted
   inside `tester.setContent`.
7. `onContentShown_firesOnEachEntryIntoContent_neverWhileLocked`: start LOCK → 0; unlock → 1;
   relock → still 1; unlock → 2. Dedupe is the activity's job.
8. `gatedRoot_carriesTheRootTag`: `CONTENT_GATE_ROOT_TAG` exists on the gate path. It gives test 9's
   string its positive counterpart.

**Showcase instrumented (the no-gate proof, B1)**:
9. `noContentGate_takesTodaysBranch` (in `apps/showcase/src/androidTest/.../GraphTest.kt` or a new file):
   launch `FoundationActivity` → Home shows → `onAllNodesWithTag("ShellContentGateRoot", useUnmergedTree = true).assertCountEquals(0)`.
   The tag is a string literal (the const is `internal`). A comment points at
   `CONTENT_GATE_ROOT_TAG`, and test 8 proves the string is real.

**Not provable in the foundation's instrumented suite**: `FoundationActivity` with a real gate, process
lifecycle, the actual UMP form and the recents flag. `:core:shell` tests run on `ComponentActivity`,
and Showcase shouldn't get a lock. These go to the **Tend row 8 card** (`feat/privacy`):
- `coldStartLocked_noContentNoConsentUntilUnlock`; `background_thenForeground_afterGrace_showsLock`;
  `notificationLinkWhileLocked_opensAfterUnlock`; `lockOn_setsFlagSecure` (API ≤ 32, manual if no AVD);
- rotation while locked stays locked; relock with a real dialog or bottom sheet in `FoundationActivity`;
  a browser link (second task) while locked; `uiautomator dump`/TalkBack while locked; the API 33+
  recents thumbnail is blank (manual);
- outgoing intents (share, SAF, browser) and relock (N4): Tend decides;
- iOS XCUITests: cold start locked (no consent, no banner), a link or notification tap while locked,
  background past grace → the lock is the first frame (this also proves R8); switcher cover manual.

**iOS (WhitelabelKit tests)**:
- `ContentGateDriverTests` (a private `NotificationCenter`, closures over a Swift var):
  `foregroundNotification_locksBeforeAnyAwait` (`foreground` sets the var to `.locked`; after
  `center.post` → `driver.state == .locked` and `screen(showsContent: true) == .lock`, synchronously),
  `backgroundNotification_callsBackgroundOnce`, `seedsFromRead`, `refresh_followsRead`, and
  `screen_pendingIsSplash`.
- `AppDefinitionTests`: `lockScreen` default nil; `contentGateProblem(bindsGate: false)` is non-nil
  with a `lockScreen`, and nil without one; `trailingClosure_stillBuildsFeatures`
  (`AppDefinition(style:, ads: []) { <one feature> }.features.count == 1`), which pins Munch's call shape.
- `scripts/check-ios.sh` (Showcase unit + UI) stays green.

## 6. Migration

- **Munch: no change**, on the identical branch. Proven by test 9, the unchanged consent tests and
  **Munch's own suites run against the branch** (Task 7). Munch picks it up only on its own pointer bump.
- **Showcase:** unchanged; regression suite plus test 9.
- **Tend:** row 7b only bumps the pointer (`chore(deps): bump foundation to <sha>`). The opt-in comes in row 8.

## 7. Risks and open questions

| # | Risk / question | Recommendation |
|---|---|---|
| R1 | A hoisted `NavHostController` re-attached to a new `NavHost` after leaving composition. | Test 5 proves it. If it fails, drop the hoist and rely on the `SaveableStateHolder` (a restored controller with the same entries). One `screen_view` per unlock happens either way (N1). |
| R2 | One stale content frame on Android warm resume. | The synchronous contract + `collectAsState` + recents protection. Tend E2E on row 8. |
| R3 | A gate that never leaves PENDING keeps the splash forever. | Documented contract. No timeout: falling back to CONTENT would defeat the lock. |
| R4 | A UMP form open during a relock stays above the lock. | Accepted (ADR 0007). |
| R5 | `FLAG_SECURE` on API 26–32 blocks the user's own screenshots. | Accepted (ADR 0007). |
| R6 | ADR 0007 §8 and R27 say iOS lifecycle comes from `scenePhase`; R25 cites `shellScreen` with a "no gate" row. | **Lead:** amend §8/R27 to "`UIApplication` will-enter-foreground / did-enter-background, observed by the kit's `ContentGateDriver`". The cover (§8, R28) stays on `scenePhase == .inactive`, which is right for the snapshot. |
| R7 | Only `rememberSaveable` and ViewModel state survive a relock. | In the `contentGate` KDoc. Fine for Tend (state in ViewModels). |
| R8 | `UIApplication` app-level notifications in a SwiftUI scene-based app: posted, and before the first active frame? | That's my understanding of UIKit (posted alongside the scene notifications). It can't be proved in the kit without a gated app. Tend's row 8 XCUITest "background past grace → lock first frame" proves it. If it fails, the fallback is `UIScene` notifications aggregated in the driver (same tests, other names). |
| R9 | Windows outside the composition (View dialogs, popups, toasts, share sheet; UIKit-presented controllers on iOS) survive a relock. | KDoc + docs rule: protected content only in Compose/SwiftUI presentations inside the app's screens, never in toasts. |

No ADR written: this is foundation API design inside ADR 0007's decision. Task 0 commits this plan to
the foundation's `docs/superpowers/plans/`.

---

# Content Gate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** an optional app-level content gate (an app lock) in the foundation that replaces the shell
content while locked, holds deep links, banners and launch consent, and reports foreground and background.
Apps without one run today's code.

**Architecture:** the contract and root-screen rule live in `:core:shell:shared`. `FoundationActivity`
branches once: no gate → today's code; gate → `GatedShell`/`ShellRoot` (splash, lock or content), with
nav state kept across relocks, process lifecycle forwarded and recents protection applied. WhitelabelKit
mirrors this with a `ContentGateDriver` on `UIApplication` notifications.

**Tech Stack:** Kotlin Multiplatform, Compose, Navigation Compose, Koin, AndroidX Lifecycle
(`lifecycle-process`), SwiftUI + SKIE.

**Spec:** this document, sections 1–7. Context: Tend ADR 0007 §8 (privacy worktree), Tend spec §6.

**Repo / branch:** foundation (`zakaria-hajja/whitelabel-app-kit`), branch `feat/content-gate` from
`main`. Foundation rules: Conventional Commits; `scripts/check.sh` before each push; **the owner
merges**. Add the `release-build` label (a dependency is declared).

## Global Constraints

- No gate declared and bound → the no-gate branch is today's code verbatim (`git diff -w` shows only
  indentation there). No `ShellRoot`, no hoisted controller, no observer, no window flags, no driver.
- `shouldGatherConsent(needsConsent, splashKept, alreadyRequested)` keeps its signature and body.
- The lock **replaces** content in composition; never an overlay.
- Recents: API 33+ `setRecentsScreenshotEnabled(false)`, API 26–32 `FLAG_SECURE`, whenever
  `protectsAppSwitcher` is true, from `onCreate`.
- Shared code: `kotlinx` only; tests in `commonTest` with `kotlin.test` + AssertK.
- Never log or report gate state beyond the enum.
- One iOS gate (`scripts/check-ios.sh`) at a time on this machine (shared simulator).

## Review Focus

1. Munch's path: `git diff -w core/shell/src/main/kotlin/com/whitelabel/core/shell/FoundationActivity.kt`
   and `RootView.swift` show the no-gate branch unchanged → test 9 (Task 4) + Task 7.
2. A notification tap on a stopped app past grace: the lock must be the first frame, and the link
   opens only after unlock → test 3 (Task 2), `collectAsState` (Task 4; the reviewer confirms it isn't
   `…WithLifecycle`), and `ContentGateDriverTests.foregroundNotification_locksBeforeAnyAwait` (Task 5).
3. Rotation while unlocked must not relock → Task 3 uses the process owner; no activity `onStop` hooks.
4. Unlock after process death returns to the same screen and opens a held link once → tests 3 and 6 (Task 2).
5. A gate bound without `contentGate {}` (or the reverse) fails at startup on both platforms →
   `checkContentGate` (Task 3), `contentGateProblem(bindsGate:)` (Task 5).

---

### Task 0: Put the plan in the foundation

**Files:** Create `docs/superpowers/plans/2026-11-14-content-gate.md` (this whole file).

- [ ] Copy this file there. Commit `docs(plan): plan the app content gate`.

### Task 1: Shared contract and root-screen rule

**Files:**
- Create: `core/shell/shared/src/commonMain/kotlin/com/whitelabel/core/shell/model/ContentGate.kt`
- Test: `core/shell/shared/src/commonTest/kotlin/com/whitelabel/core/shell/model/ContentGateTest.kt`

**Interfaces — Produces:** `ContentGateState`, `ContentGate`, `ShellScreen`,
`shellScreen(showsContent: Boolean, gate: ContentGateState): ShellScreen` (non-null `gate`),
`contentGateProblem(declaresLockScreen: Boolean, bindsGate: Boolean): String?`, exactly as in §3.1,
KDoc included.

- [ ] **Step 1: Write failing tests**
```kotlin
@Test fun pending_keepsSplashEvenWhenReady() = assertThat(shellScreen(true, ContentGateState.PENDING)).isEqualTo(ShellScreen.SPLASH)
@Test fun decidedGate_waitsForShell() {
    assertThat(shellScreen(false, ContentGateState.LOCKED)).isEqualTo(ShellScreen.SPLASH)
    assertThat(shellScreen(false, ContentGateState.OPEN)).isEqualTo(ShellScreen.SPLASH)
}
@Test fun locked_isLock() = assertThat(shellScreen(true, ContentGateState.LOCKED)).isEqualTo(ShellScreen.LOCK)
@Test fun open_isContent() = assertThat(shellScreen(true, ContentGateState.OPEN)).isEqualTo(ShellScreen.CONTENT)
@Test fun gateWiring() {
    assertThat(contentGateProblem(false, false)).isNull()
    assertThat(contentGateProblem(true, true)).isNull()
    assertThat(contentGateProblem(true, false)).isEqualTo("contentGate() needs a ContentGate bound in the app's modules")
    assertThat(contentGateProblem(false, true)).isEqualTo("a ContentGate is bound but the app definition doesn't call contentGate()")
}
```
- [ ] **Step 2:** `./gradlew :core:shell:shared:allTests` → FAIL (unresolved references).
- [ ] **Step 3:** Implement `ContentGate.kt` per §3.1.
- [ ] **Step 4:** `./gradlew :core:shell:shared:allTests` → PASS (JVM + iOS simulator).
- [ ] **Step 5:** Commit `feat(shell): add the shared content gate contract`.

### Task 2: `ShellRoot`: lock replaces content, state kept

**Files:**
- Create: `core/shell/src/main/kotlin/com/whitelabel/core/shell/ShellRoot.kt`
- Test: `core/shell/src/androidTest/kotlin/com/whitelabel/core/shell/ContentGateTest.kt`

**Interfaces:**
- Consumes: `ShellScreen` (Task 1).
- Produces: `internal const val CONTENT_GATE_ROOT_TAG = "ShellContentGateRoot"`,
  `internal const val LOCK_SCREEN_TAG = "ShellLockScreen"`, `internal const val SHELL_STATE_KEY = "shell"`,
  `@Composable internal fun ShellRoot(screen: ShellScreen, lockScreen: @Composable () -> Unit, onContentShown: () -> Unit, content: @Composable () -> Unit)`.

- [ ] **Step 1: Write failing tests** 1–8 from §5. The harness mirrors `GateTest.show()`:
  ```kotlin
  var screen by mutableStateOf(ShellScreen.CONTENT)
  rule.setContent {
      nav = rememberNavController()
      AppTheme {
          ShellRoot(screen, { Text("Lock screen") }, { shown++ }) {
              SideEffect { contentCompositions++ }
              ShellNavHost(definition, definition.tabs, "test", deepLink = deepLink,
                  onDeepLinkConsumed = { consumed++; deepLink = null }, navController = nav,
                  onScreenView = { screenViews += it })
          }
      }
  }
  ```
  Test 6 uses `StateRestorationTester(rule)` with `rememberNavController()` inside `tester.setContent`.
  No ads or Koin rig is needed (test 2 counts content compositions).
- [ ] **Step 2:** `./gradlew :core:shell:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.whitelabel.core.shell.ContentGateTest`
  → FAIL to compile (no `ShellRoot`).
- [ ] **Step 3:** Implement `ShellRoot` per §3.2.
- [ ] **Step 4:** Step 2's command → 8/8 PASS. Then the whole `:core:shell:connectedDebugAndroidTest`
  (`GateTest`, `ShellNavHostTest`, `SharedElementTest` unchanged) → PASS. If test 5 fails on the hoisted
  controller (R1), stop and report to the Lead with the failure.
- [ ] **Step 5:** Commit `feat(shell): replace the shell with a lock screen while gated`.

### Task 3: Definition hook, startup check, lifecycle forwarding

**Files:**
- Modify: `core/shell/src/main/kotlin/com/whitelabel/core/shell/AppDefinition.kt` (`lockScreen`,
  `contentGate(lockScreen)` with the §3.2 KDoc)
- Modify: `core/shell/src/main/kotlin/com/whitelabel/core/shell/FoundationApplication.kt`
- Create: `core/shell/src/main/kotlin/com/whitelabel/core/shell/ContentGateLifecycle.kt`
- Modify: `gradle/libs.versions.toml` (`androidx-lifecycle-process = { module = "androidx.lifecycle:lifecycle-process", version.ref = "lifecycle" }`; check the existing version key's name), `core/shell/build.gradle.kts` (`implementation(libs.androidx.lifecycle.process)`)
- Test: `core/shell/src/test/kotlin/com/whitelabel/core/shell/AppDefinitionTest.kt`, new `ContentGateLifecycleTest.kt`, new `CheckContentGateTest.kt`

**Interfaces:**
- Consumes: `ContentGate`, `contentGateProblem` (Task 1).
- Produces: `AppDefinition.lockScreen: (@Composable () -> Unit)?` (internal);
  `AppDefinitionBuilder.contentGate(lockScreen: @Composable () -> Unit)`;
  `internal class ContentGateLifecycle(gate: ContentGate) : DefaultLifecycleObserver`;
  `internal fun checkContentGate(definition: AppDefinition, gate: ContentGate?)`, which throws
  `IllegalStateException` with the `contentGateProblem` message.

- [ ] **Step 1: Write failing tests:** `contentGate_setsLockScreen`, `noContentGate_lockScreenIsNull`;
  `ContentGateLifecycleTest.startAndStop_forwardOnce` (`LifecycleRegistry.createUnsafe`,
  `CREATED→STARTED→RESUMED→STARTED→CREATED`, the fake records `[FOREGROUND, BACKGROUND]`);
  `checkContentGate_lockScreenWithoutGate_throws`, `_gateWithoutLockScreen_throws`, `_neither_passes`.
- [ ] **Step 2:** `./gradlew :core:shell:testDebugUnitTest` → FAIL (unresolved).
- [ ] **Step 3:** Implement per §3.2 "Lifecycle and startup". Register and remove the observer on the
  main thread (`Looper.myLooper() == Looper.getMainLooper()` → direct, else
  `Handler(Looper.getMainLooper()).post`). Keep the observer reference for removal.
- [ ] **Step 4:** `./gradlew :core:shell:testDebugUnitTest` → PASS.
- [ ] **Step 5:** Commit `feat(shell): declare a content gate and report the app lifecycle to it`.

### Task 4: FoundationActivity: the one branch, consent, recents

**Files:**
- Modify: `core/shell/src/main/kotlin/com/whitelabel/core/shell/FoundationActivity.kt`
- Create: `core/shell/src/main/kotlin/com/whitelabel/core/shell/RecentsProtection.kt`
- Test: `core/shell/src/test/kotlin/com/whitelabel/core/shell/RecentsProtectionTest.kt`;
  `apps/showcase/src/androidTest/kotlin/com/whitelabel/apps/showcase/GraphTest.kt` (test 9)
- **Not modified:** the `shouldGatherConsent` assertions in `core/shell/src/test/kotlin/com/whitelabel/core/shell/FirebaseWiringTest.kt`.

**Interfaces:**
- Consumes: `ShellRoot`, `CONTENT_GATE_ROOT_TAG` (Task 2); `ContentGate`, `shellScreen` (Task 1);
  `AppDefinition.lockScreen` (Task 3).
- Produces: `RecentsProtection`, `recentsProtection(sdkInt: Int, on: Boolean): RecentsProtection`,
  `Activity.applyRecentsProtection(p: RecentsProtection)` (all internal); private `requestConsent()`,
  `requestConsentIfDue()`, `GatedShell(...)` in `FoundationActivity.kt`.

- [ ] **Step 1: Write failing tests:**
  - `recentsProtection(33, true) == NO_SCREENSHOT`, `(32, true) == FLAG_SECURE`, `(26, true) == FLAG_SECURE`, `(36, false) == NONE`;
  - test 9 `noContentGate_takesTodaysBranch` (§5).
- [ ] **Step 2:** `./gradlew :core:shell:testDebugUnitTest` → FAIL (unresolved). Test 9 can't go red
  before this task (no tag exists yet), so it's a regression guard, not a TDD driver. Test 8 is what proves its string can match.
- [ ] **Step 3:** Implement per §3.2 "FoundationActivity". The variable is `contentGate`. Splash
  condition and `setContent` branch exactly as sketched. `GatedShell` collects with `collectAsState()`.
  `onContentShown = ::requestConsentIfDue`. Recents collection is in `onCreate` on the gate path only.
- [ ] **Step 4:** Verify:
  - `git diff -w main -- core/shell/src/main/kotlin/com/whitelabel/core/shell/FoundationActivity.kt`:
    the no-gate lines show no change except moving into the `if`;
  - `scripts/check.sh` → PASS (including the unmodified `FirebaseWiringTest`);
  - `./gradlew :core:shell:connectedDebugAndroidTest :apps:showcase:connectedDebugAndroidTest` → all PASS, test 9 included.
- [ ] **Step 5:** Commit `feat(shell): hold consent and protect recents behind the content gate`.

### Task 5: iOS counterpart

**Files:**
- Modify: `core/ios/src/iosMain/kotlin/com/whitelabel/core/ios/SharedGraph.kt` (`fun contentGateOrNull(): ContentGate? = koin.getOrNull()`)
- Modify: `ios/WhitelabelKit/Sources/WhitelabelKit/Shell/AppDefinition.swift` (`lockScreen:` after `ads:`; `func contentGateProblem(bindsGate: Bool) -> String?`)
- Create: `ios/WhitelabelKit/Sources/WhitelabelKit/Shell/ContentGateDriver.swift`
- Modify: `ios/WhitelabelKit/Sources/WhitelabelKit/Shell/RootView.swift` (`AppRuntime.gateDriver`, the startup check, the `for await` loop, `ShellContent` branch + `GatedContent`, `processPendingLink`)
- Test: new `ios/WhitelabelKit/Tests/WhitelabelKitTests/ContentGateDriverTests.swift`; `AppDefinitionTests.swift`

**Interfaces:**
- Consumes: `ContentGate`, `ContentGateState`, `ShellScreen`, `ContentGateKt.shellScreen`,
  `ContentGateKt.contentGateProblem` (check the SKIE names in the generated header after `:core:ios` builds).
- Produces: `ContentGateDriver` (§3.3 signature);
  `AppDefinition(…, ads:, lockScreen: (@MainActor () -> AnyView)? = nil, _ features:)`;
  `AppDefinition.contentGateProblem(bindsGate:)`.

- [ ] **Step 1: Write failing tests:** the `ContentGateDriverTests` and `AppDefinitionTests` cases in §5.
  The key one:
  ```swift
  var current = ContentGateState.open
  let center = NotificationCenter()
  let driver = ContentGateDriver(read: { current }, foreground: { current = .locked }, background: {}, center: center)
  center.post(name: UIApplication.willEnterForegroundNotification, object: nil)
  XCTAssertEqual(driver.state, .locked)
  XCTAssertEqual(driver.screen(showsContent: true), .lock)
  ```
- [ ] **Step 2:** `scripts/check-ios.sh` → FAIL (no `ContentGateDriver`).
- [ ] **Step 3:** Implement per §3.3. `gateDriver` is nil without a bound gate, and then `ShellContent.content`
  and `processPendingLink` take today's path verbatim.
- [ ] **Step 4:** `./gradlew :core:ios:allTests` and `scripts/check-ios.sh` → PASS. Check
  `git diff -w main -- ios/WhitelabelKit/Sources/WhitelabelKit/Shell/RootView.swift`: today's `content` builder is unchanged.
- [ ] **Step 5:** Commit `feat(ios): gate the app content behind an app lock`.

### Task 6: Docs and the foundation's full gate

**Files:**
- Modify: `docs/creating-an-app.md` (or wherever `ads {}` is documented): a "Content gate (app lock)"
  section. Cover the one-line opt-in per platform; the contract (PENDING never returns, synchronous
  `onAppForeground`, Android's launch call, cheap binding, `protectsAppSwitcher`); what is held; one
  `screen_view` per unlock; R7; R9.
- Modify: `docs/ios.md`: `lockScreen:`, the `UIApplication` signals, and that the switcher cover stays the app's.

- [ ] **Step 1:** Write the docs.
- [ ] **Step 2:** `scripts/check.sh`, `./gradlew :core:shell:connectedDebugAndroidTest :apps:showcase:connectedDebugAndroidTest`,
  `scripts/check-ios.sh` → all PASS. Record the counts.
- [ ] **Step 3:** Commit `docs: document the content gate`. Don't open the PR yet.

### Task 7: Munch's own suites against the branch, then the PR

Nothing is committed in Munch. This is a local check with `foundation/` checked out at this branch's head.

- [ ] **Step 1: Set up a Munch worktree.**
  ```bash
  git -C /Users/zakariahajja/Whitelabel/munch worktree add --detach ../munch-content-gate-check origin/main
  cd /Users/zakariahajja/Whitelabel/munch-content-gate-check
  git submodule update --init
  cp ../munch/local.properties .
  git -C foundation fetch <absolute path of the foundation checkout holding feat/content-gate> feat/content-gate
  git -C foundation checkout --detach FETCH_HEAD
  git -C foundation log --oneline -1    # must be the branch head
  ```
- [ ] **Step 2:** `scripts/check.sh` → PASS (record the test count).
- [ ] **Step 3:** With an emulator running:
  `./gradlew :app:connectedDebugAndroidTest :munch:creatures:connectedDebugAndroidTest :munch:rewards:connectedDebugAndroidTest :munch:debug:connectedDebugAndroidTest`
  → all PASS. `RewardsFlowTest` and `TabsTest` cover banners and consent. Record the counts.
- [ ] **Step 4:** `scripts/check-ios.sh` → PASS. Run it directly, not through the hook (Munch's hook
  still has the GIT_DIR leak, Trello CTb5rJEy), and alone (one iOS gate at a time). It also compiles
  Munch's trailing-closure `AppDefinition` calls.
- [ ] **Step 5:** If anything fails, stop and report to the Lead with the failure. Don't patch Munch.
  Leave the worktree in place for the owner to remove (it contains a submodule).
- [ ] **Step 6:** Push `feat/content-gate`, then `gh pr create` from the foundation's template with the
  `release-build` label. The body has:
  - the foundation counts;
  - **Munch counts (check.sh, the four connected modules, check-ios) with the commits they ran
    against**;
  - the `git diff -w` note for the no-gate branch;
  - the row 8 E2E list that Tend owes.

  **The owner merges.** After the merge, Tend gets `chore(deps): bump foundation to <sha>` on its own branch.

---

## Implementation notes (design review round 2, folded in)

The design passed review round 2 (APPROVED) with seven non-blocking notes. They apply to the tasks above:

1. **Task 4: test the gate path's consent rule.** Extract `internal fun gatedSplash(screen: ShellScreen): GatedSplash`
   (`keep`, `splashKept`) next to `shouldGatherConsent`, and unit-test SPLASH → (true, true), LOCK → (false, true),
   CONTENT → (false, false). The splash condition's gate side calls it; the no-gate side stays as it is.
2. **Task 5: the iOS startup check runs after the graph starts.** `serviceProblem` runs before `start(...)`, when Koin
   isn't up. Check `contentGateProblem(bindsGate:)` right after `try start(...)`, before building `AppRuntime`.
3. **Task 4, test 9: harness.** `GraphTest` doesn't launch `FoundationActivity`, and Showcase has a sign-in gate, so
   its first screen is Login. Test 9 goes in `CleanStartTest` (the `FoundationActivity` rule + `FreshAppRule`): the
   sign-in screen shows, then the tag count is 0.
4. **Task 5: SKIE and the flow's element type.** `gate.state` is `StateFlow<ContentGateState>`; in a generic position
   SKIE may expose the ObjC class instead of the Swift enum. Check the generated header for the flow's element type
   and bridge it if needed.
5. **Task 5: observer closures.** Write the notification handlers as `MainActor.assumeIsolated { … }` so they stay
   synchronous. Never hop with `Task { @MainActor in }` (that brings back the B4 lag).
6. **Task 4: smart cast.** Bind `val gate = contentGate` once at the top of `onCreate` and use it in both the splash
   condition and `setContent`.
7. **Nit:** test 7's sequence is 0 → 1 → 1 → 2 (§5 is authoritative).
