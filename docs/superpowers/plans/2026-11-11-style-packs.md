# Style Packs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An app picks a look-and-motion preset in one line (`style(StylePack.Gallery)`). Shared components and screen transitions follow it, and showcase and globex stay exactly as they are.

**Architecture:** `StylePack` is a plain value in `:core:designsystem` (type tweaks, corners, surface style, sizing, motion, haptics) with no Compose runtime inside, so it's unit-testable. `AppTheme(stylePack)` provides it through `LocalStylePack` and builds the Material theme from it. The shell takes the pack from the app definition, picks NavHost transitions from it, and wraps the NavHost in `SharedTransitionLayout`. A `screen<Route>` builder exposes each destination's animation scope to the design system's shared-element helpers.

**Tech Stack:** Kotlin, Compose (BOM 2025.10.01, shared transitions `@ExperimentalSharedTransitionApi`), Navigation Compose 2.9.5, Hilt, JUnit 5 + AssertK, Compose UI tests.

**Spec:** `docs/superpowers/specs/2026-11-10-standalone-app-support-design.md` §3 (and §7 row 2). Gallery values: the Munch design (`munch/docs/design/tokens.json`, Motion page in `munch/docs/design/tools/gen_design.py` `motion_page`).

**Branch:** `feat/style-packs` from `origin/main` (b184658), worktree `../android-starter-kit-style`.

## Global Constraints

- **Anti-goal:** showcase and globex must look and behave exactly as before. `StylePack.Material` reproduces today's values: `brand_corner_*` corners, 48 dp controls, shadow (elevated) cards, no haptics, and Navigation's default 700 ms fades.
- Colors and fonts stay in brand resources. A pack never sets either.
- No app identity in `core/*`. `StylePack.Gallery` is a generic preset (named after a look, not after Munch).
- Shared modules never depend on apps. Conventional Commits; `scripts/check.sh` before every push; each commit ends with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- **Reduced motion:** when the system animator duration scale is 0 ("Remove animations"), every motion spec resolves to a 150 ms linear crossfade, and the tab-switch scale is 1.

## Review Focus

- **The animator duration scale is 0:** transitions become 150 ms crossfades. Nothing springs or slides, and nothing breaks. (Task 1 tests `reduced()`; Task 2 tests the setting is read.)
- **A shared-element modifier used outside the shell** (previews, tests, a screen hosted elsewhere) is a no-op, not a crash. (Task 3)
- **Back from a pushed screen to a tab:** uses the screen-exit spec, not the tab-switch one. Only tab→tab uses the tab switch. (Task 3, pure selection test)
- **An app that never calls `style()`** gets `Material`, and its theme values match today's. (Task 2 instrumented parity test)
- **Calling `style()` twice** in a definition: the last call wins, and the build doesn't fail. (Task 3 unit test)

---

### Task 1: The `StylePack` model and the two packs

**Files:**
- Create: `core/designsystem/src/main/kotlin/com/whitelabel/core/designsystem/theme/StylePack.kt` (the model and the two presets)
- Create: `core/designsystem/src/main/kotlin/com/whitelabel/core/designsystem/theme/Motion.kt` (`MotionCurve`, `MotionSpec`, conversion to Compose specs)
- Test: `core/designsystem/src/test/kotlin/com/whitelabel/core/designsystem/theme/StylePackTest.kt`

**Interfaces (produced):**
```kotlin
data class StylePack(
    val name: String,
    val type: TypeTweaks,
    val corners: Corners?,          // null = brand_corner_* resources
    val surfaces: SurfaceStyle,
    val sizing: Sizing,
    val motion: MotionSpec,
    val haptics: Map<HapticEvent, HapticPattern>,
) {
    fun reduced(): StylePack        // motion = MotionSpec.Reduced; everything else unchanged
    companion object { val Material: StylePack; val Gallery: StylePack }
}
data class TypeTweaks(val displayWeight: FontWeight?, val headlineWeight: FontWeight?, val titlesUseBodyFont: Boolean)
data class Corners(val small: Dp, val medium: Dp, val large: Dp)
enum class SurfaceStyle { Shadow, Hairline }
data class Sizing(val controlHeight: Dp, val listRowMinHeight: Dp, val screenGutter: Dp)
enum class HapticEvent { Confirm, Success, Toggle }
enum class HapticPattern { Tick, Confirm, Toggle }

sealed interface MotionCurve {
    data class Tween(val durationMs: Int, val easing: CubicBezier) : MotionCurve
    data class Spring(val dampingRatio: Float, val stiffness: Float) : MotionCurve
}
data class CubicBezier(val a: Float, val b: Float, val c: Float, val d: Float)
data class MotionSpec(
    val screenEnter: MotionCurve, val screenExit: MotionCurve, val sharedElement: MotionCurve,
    val tabSwitch: MotionCurve, val tabSwitchScale: Float, val emphasis: MotionCurve, val snackbar: MotionCurve,
) { companion object { val Material: MotionSpec; val Gallery: MotionSpec; val Reduced: MotionSpec } }
fun <T> MotionCurve.toAnimationSpec(): FiniteAnimationSpec<T>   // Tween → tween(durationMs, CubicBezierEasing), Spring → spring(...)
```

- [ ] **Step 1: Write the failing tests** (`StylePackTest`):
  - `material reproduces today`: `corners == null`, `surfaces == Shadow`, `sizing == Sizing(48.dp, 56.dp, 16.dp)`, `haptics.isEmpty()`, `type == TypeTweaks(null, null, false)`, `motion.screenEnter == Tween(700, CubicBezier(0.4f, 0f, 0.2f, 1f))` (Navigation's default `tween(700)` with FastOutSlowIn), and `screenExit` and `tabSwitch` are equal to it, `tabSwitchScale == 1f`.
  - `gallery matches the design`:
    - `corners == Corners(4.dp, 8.dp, 14.dp)`, `surfaces == Hairline`, `sizing == Sizing(52.dp, 72.dp, 20.dp)`;
    - `type == TypeTweaks(FontWeight.Normal, FontWeight.Normal, titlesUseBodyFont = true)`;
    - motion: `sharedElement == screenEnter == Tween(420, CubicBezier(0.2f, 0f, 0f, 1f))`, `screenExit == Tween(320, CubicBezier(0.3f, 0f, 0.8f, 0.15f))`, `tabSwitch == Tween(210, CubicBezier(0.2f, 0f, 0f, 1f))`, `tabSwitchScale == 0.96f`, `emphasis == Spring(0.55f, 380f)`, `snackbar == Tween(200, CubicBezier(0.2f, 0f, 0f, 1f))`;
    - `haptics == mapOf(Confirm to Tick, Success to HapticPattern.Confirm, Toggle to HapticPattern.Toggle)`.
  - `reduced collapses every curve to a 150 ms linear crossfade`: for both packs, every curve in `reduced().motion` is `Tween(150, CubicBezier(0f, 0f, 1f, 1f))`, `tabSwitchScale == 1f`, and every non-motion field is unchanged.
  - `copy allows app variation`: `Gallery.copy(sizing = Gallery.sizing.copy(controlHeight = 56.dp)).sizing.controlHeight == 56.dp`.
- [ ] **Step 2: Run** `./gradlew :core:designsystem:testDebugUnitTest --tests '*StylePackTest'`. Expected: FAIL (unresolved references).
- [ ] **Step 3: Implement** the two files with the values above. `toAnimationSpec` maps `Tween` to `tween(durationMs, easing = CubicBezierEasing(a, b, c, d))` and `Spring` to `spring(dampingRatio, stiffness)`.
- [ ] **Step 4: Run** the same command. Expected: PASS.
- [ ] **Step 5: Commit** `feat(designsystem): add style packs: Material (today's look) and Gallery`

### Task 2: The theme and components follow the pack

**Files:**
- Modify: `theme/AppTheme.kt` (`AppTheme(darkTheme, stylePack = StylePack.Material, content)`; provides `LocalStylePack`, and `LocalReducedMotion` from the system setting; when reduced, provides `stylePack.reduced()`; adds `AppTheme.style` and `AppTheme.reducedMotion` getters)
- Modify: `theme/BrandTypography.kt` (`brandTypography(tweaks: TypeTweaks)`: display weight, headline weight, and with `titlesUseBodyFont` the title styles use the body font)
- Modify: `theme/BrandShapes.kt` (`brandShapes(corners: Corners?)`: `null` keeps the resource lookup)
- Create: `theme/Haptics.kt` (`@Composable fun rememberHaptics(): (HapticEvent) -> Unit`, which plays the pack's pattern through `LocalHapticFeedback`: Tick → `HapticFeedbackType.SegmentTick`, Confirm → `HapticFeedbackType.Confirm`, Toggle → `HapticFeedbackType.ToggleOn`; an unmapped event does nothing)
- Create: `theme/ReducedMotion.kt` (`internal fun isReducedMotion(animatorDurationScale: Float): Boolean = animatorDurationScale == 0f`, plus a `@Composable` reader of `Settings.Global.ANIMATOR_DURATION_SCALE`, default 1f)
- Modify: `component/AppButtons.kt` (min height = `AppTheme.style.sizing.controlHeight`), `component/AppTextField.kt` (same min height), `component/AppCard.kt` (`Hairline` uses `OutlinedCard` with a 1 dp `outlineVariant` border and no elevation; `Shadow` stays `Card`)
- Modify: `catalog/DesignSystemCatalog.kt` (a line showing the active pack's name)
- Test: `src/test/.../theme/ReducedMotionTest.kt`; `src/androidTest/.../theme/AppThemePackTest.kt`

**Interfaces:**
- Consumes: Task 1's model.
- Produces: `AppTheme(darkTheme: Boolean = isSystemInDarkTheme(), stylePack: StylePack = StylePack.Material, content: @Composable () -> Unit)`; `AppTheme.style: StylePack` (already reduced when the setting is on); `AppTheme.reducedMotion: Boolean`; `rememberHaptics()`.

- [ ] **Step 1: Write the failing tests.**
  - Unit `ReducedMotionTest`: `0f` → true; `1f`, `0.5f`, `10f` → false.
  - Instrumented `AppThemePackTest`:
    - `material keeps today's theme`: inside `AppTheme(stylePack = Material)`, `MaterialTheme.shapes.small == RoundedCornerShape(8.dp)` (the designsystem default `brand_corner_small`), `AppTheme.style == StylePack.Material`, and `MaterialTheme.typography.displayLarge.fontWeight == Typography().displayLarge.fontWeight`.
    - `gallery applies corners and type`: `shapes.small == RoundedCornerShape(4.dp)`, `typography.displayLarge.fontWeight == FontWeight.Normal`.
    - `gallery card is outlined`: an `AppCard` under Gallery has no shadow elevation. Assert through a semantics/test tag, or by checking the `OutlinedCard` border is drawn: give `AppCard` the test tag `"AppCard:Hairline"` / `"AppCard:Shadow"`.
- [ ] **Step 2: Run** `./gradlew :core:designsystem:testDebugUnitTest`. Expected: FAIL. Then `./gradlew :core:designsystem:connectedDebugAndroidTest` on `emulator-5554`. Expected: FAIL.
- [ ] **Step 3: Implement** as listed. The `AppTheme` signature is source-compatible: existing callers keep compiling and get `Material`.
- [ ] **Step 4: Run** both commands again (expected: PASS), then `scripts/check.sh` (expected: passes).
- [ ] **Step 5: Commit** `feat(designsystem): theme and components follow the active style pack`

### Task 3: The shell: `style()`, transitions and shared elements

**Files:**
- Modify: `core/shell/build.gradle.kts` (`:core:designsystem` from `implementation` to `api`, since apps pass `StylePack` to `style()`)
- Modify: `AppDefinition.kt` (`AppDefinition.stylePack: StylePack` internal, default `Material`; `AppDefinitionBuilder.style(pack: StylePack)`, where the last call wins)
- Modify: `FoundationActivity.kt` (`AppTheme(darkTheme, stylePack = definition.stylePack)`)
- Create: `core/shell/src/main/kotlin/com/whitelabel/core/shell/ShellTransitions.kt`:
  - `internal fun transitionKind(fromRoute: String?, toRoute: String?, tabRoutes: Set<String>): TransitionKind`, where `enum TransitionKind { TabSwitch, Screen }`. It's `TabSwitch` only when both routes are tab roots.
  - The NavHost enter/exit/popEnter/popExit lambdas built from `AppTheme.style.motion`:
    - `TabSwitch` is a fade-through, `fadeIn + scaleIn(initialScale = tabSwitchScale)` / `fadeOut` with `tabSwitch`.
    - `Screen` uses `fadeIn(screenEnter)` / `fadeOut(screenExit)` for both push and pop.
- Create: `core/designsystem/src/main/kotlin/com/whitelabel/core/designsystem/motion/SharedElements.kt`:
  - `val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }`;
  - `val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }`;
  - `@Composable fun Modifier.sharedElementTransition(key: Any): Modifier` and `Modifier.sharedBoundsTransition(key: Any)`. Each uses both locals and the pack's `sharedElement` curve as `boundsTransform`, and returns `this` unchanged when either local is null.
- Create: in the shell, `inline fun <reified T : Any> NavGraphBuilder.screen(deepLinks: List<NavDeepLink> = emptyList(), noinline content: @Composable (NavBackStackEntry) -> Unit)`. It wraps `composable<T>` and provides `LocalNavAnimatedVisibilityScope` = the destination's `AnimatedContentScope`.
- Modify: `ShellNavHost.kt` (wrap both NavHosts in `SharedTransitionLayout`, provide `LocalSharedTransitionScope`, pass the transition lambdas; the bottom bar gets a 1 dp `outlineVariant` top border under `Hairline`)
- Modify: `apps/globex/notes/.../Notes.kt` and the diagnostics entry to use `screen<…>` (behavior unchanged; it proves the helper)
- Test: `core/shell/src/test/.../ShellTransitionsTest.kt`, `AppDefinitionTest.kt`; `core/shell/src/androidTest/.../SharedElementTest.kt`; `core/designsystem/src/androidTest/.../SharedElementNoOpTest.kt`

**Interfaces:**
- Consumes: `AppTheme(…, stylePack)`, `AppTheme.style` (Task 2).
- Produces: `style(pack)`, `screen<T> { }`, `Modifier.sharedElementTransition(key)`, `Modifier.sharedBoundsTransition(key)`, and the two locals.

- [ ] **Step 1: Write the failing tests.**
  - `ShellTransitionsTest`:
    - `tab to tab is a tab switch`: `transitionKind("A", "B", setOf("A", "B")) == TabSwitch`.
    - `tab to pushed screen and back are screen transitions`: `("A", "Detail")` and `("Detail", "A")` → `Screen`.
    - `null routes are screen transitions`: `(null, "A")` → `Screen`.
  - `AppDefinitionTest`:
    - `style defaults to material`;
    - `last style call wins`: `style(Gallery); style(Material)` → `Material`.
  - `SharedElementNoOpTest` (designsystem, instrumented): a `Box(Modifier.sharedElementTransition("k"))` inside `AppTheme { }` with no shell renders and is displayed.
  - `SharedElementTest` (shell, instrumented): `ShellNavHost` with a tab destination and a pushed `screen<Detail>`, each with a `Box(Modifier.sharedElementTransition("hero").testTag(...))`. Navigate to `Detail` and back with `mainClock` advanced past 420 ms. The detail box is displayed after the push, the list box after the pop, and nothing throws. Run it under both `Material` and `Gallery`.
- [ ] **Step 2: Run** `./gradlew :core:shell:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3: Implement** as listed. NavHost route strings come from `NavDestination.route`. Tab routes are the visible tabs' route class qualified names, the same form the NavDestination route starts with; match with `startsWith` and pin it in the unit test as `"com.x.A"`-style strings.
- [ ] **Step 4: Run** the unit tests (expected: PASS). Then run `:core:shell:connectedDebugAndroidTest` and `:core:designsystem:connectedDebugAndroidTest` on `emulator-5554` (expected: PASS). Then `scripts/check.sh`, `./gradlew assembleDebug assembleRelease` and `scripts/verify-apps.sh`. Expected: all pass, with the same OK count as on `main`.
- [ ] **Step 5: Manual parity check:** install showcase debug on the emulator, switch tabs, and open a screen. Transitions look as they do on `main` (700 ms fades, no haptics, same corners).
- [ ] **Step 6: Commit** `feat(shell): apply the app's style pack, with shared-element transitions`

### Task 4: Docs

**Files:**
- Modify: `docs/branding.md` (a new section "Style packs": what a pack sets and what stays in resources, `style(StylePack.Gallery)`, `copy(...)` for variations, reduced motion, and `brand_corner_*` being ignored when a pack sets `corners`)
- Modify: `docs/adding-a-shared-feature.md` (register destinations with `screen<Route>`; use `sharedElementTransition(key)` / `sharedBoundsTransition(key)`; read `AppTheme.style.motion` for in-screen animations, and `rememberHaptics()`)
- Modify: `docs/architecture.md` (the designsystem row mentions style packs)
- Modify: `docs/creating-a-standalone-app.md` §6 (CI): replace the checkout snippet with the setup Munch proved works. Munch is checked out with the job token and `persist-credentials: false`; a "Require FOUNDATION_READ_TOKEN" step; a submodule fetch that passes the token on one command as an `extraheader` scoped to the foundation URL; the token needs Contents: read on the foundation repo only.

- [ ] **Step 1: Write the docs.** Every snippet uses the names from Tasks 1–3 exactly.
- [ ] **Step 2: Verify** each code identifier in the docs exists, by grepping for `style(`, `screen<`, `sharedElementTransition`, `rememberHaptics` and `StylePack.Gallery` in `core/`.
- [ ] **Step 3: Commit** `docs: explain style packs and shared-element transitions`
