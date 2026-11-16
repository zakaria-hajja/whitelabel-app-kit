# Calm Presses and Tabs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Gallery-pack presses become a steady 120 ms scale to 0.97 with no bounce, rows tint on press, and tab switches become a 150 ms crossfade with no zoom, with reduced motion respected.

**Architecture:** Every number goes into the existing style-pack `MotionSpec` (Kotlin is the source, Swift mirrors it and `StylePackTests` pins the copy). Two new named fields, `press` and `pressScale`, carry the press motion. The tab switch keeps its existing `tabSwitch`/`tabSwitchScale` fields with new Gallery values. No new modules, no new dependencies, no change to the shell.

**Tech Stack:** Kotlin + Compose (`:core:designsystem`), SwiftUI (`ios/WhitelabelKit`), JUnit5 + AssertK, XCTest.

**Spec:** Trello card P8 https://trello.com/c/V0QXfTL3; Munch motion proposal `../munch-motion/docs/design/motion/index.html#p8`, audit `../munch-motion/docs/design/motion/audit.md` (findings 6 and 7), tokens `../munch-motion/docs/design/tokens.json` (`motion.press`, `motion.crossfade`).

## What exists today (read before starting)

| Concern | Android | iOS |
|---|---|---|
| Motion tokens | `core/designsystem/src/main/kotlin/com/whitelabel/core/designsystem/theme/Motion.kt` (`MotionSpec.Material`, `Gallery`, `Reduced`) | `ios/WhitelabelKit/Sources/WhitelabelKit/DesignSystem/Motion.swift` (mirror) |
| Reduced motion | `AppTheme` swaps in `StylePack.reduced()` (`MotionSpec.Reduced`) when the animator scale is 0 | `Environment+Style.swift` swaps in `pack.reduced()` under Reduce Motion |
| Button press | Material ripple (`AppButtons.kt`): **no change**, the proposal keeps the ripple on Android | `AppButtonStyles.swift:81-86` `pressEffect`: scale 0.98 + opacity 0.9 on `motion.emphasis` (Gallery: spring, damping 0.55, so it wobbles) |
| Row press | `clickable` ripple on `SettingsValueRow`/`SettingsLinkRow` (`feature/settings/.../SettingsRows.kt`) and `PrivacyChoicesRow` (`core/shell/.../consent/PrivacyChoicesRow.kt`): the ripple is the tint, **no change** | `AppValueRow` (`DesignSystem/Components/AppListRows.swift:21`) uses `.buttonStyle(.plain)`: only a faint dim |
| Tab switch | `core/shell/.../ShellTransitions.kt:54-67`: `fadeIn + scaleIn(initialScale = tabSwitchScale)` on `motion.tabSwitch`; with scale 1 the `scaleIn` is a no-op, so **no shell change** | Native `TabView` (`Shell/TabsView.swift`): switches instantly, no zoom, `tabSwitch` is not read. **No change** |

Which apps see it: only `StylePack.Gallery` changes (Munch on both platforms; the showcase uses `Material`). `Material` keeps today's values exactly ("Material reproduces today"). No screenshot tests exist in the foundation. Tests that pin the changed values: `core/designsystem/src/test/.../theme/StylePackTest.kt` and `ios/WhitelabelKit/Tests/WhitelabelKitTests/StylePackTests.swift`. Munch's `MunchDefinitionTest` (`MunchStyle.copy(...) == Gallery`) keeps passing after the bump. Munch's `BadgeCard.kt:42` reads `motion.emphasis`, which this plan does not touch.

## Global Constraints

- Press: `Tween(120, ease-out CubicBezier(0.32, 0.72, 0, 1))`, scale `0.97`, no spring, no bounce (tokens.json `motion.press`).
- Tab switch (Gallery): `Tween(150, Standard = CubicBezier(0.2, 0, 0, 1))`, `tabSwitchScale = 1` (proposal P8: "tabSwitch = Tween(150, Standard), tabSwitchScale = 1f").
- Rows tint on press instead of scaling; the tint reuses the `press` curve (120 ms, inside the proposal's 100–150 ms) so there is one press token, not two.
- Reduced motion: press has no scale and is instant (`Tween(0, Linear)`), the tint stays (it is the feedback); navigation, tabs included, keeps `MotionSpec.Reduced`'s 150 ms linear crossfade (tokens.json `motion.reduced`). `MotionSpec.Reduced`'s existing curves do not change.
- `Material` pack: `press = Spring(dampingRatio = 1, stiffness = 400)` and `pressScale = 0.98` (today's iOS press on Material), so nothing changes for Material apps.
- Swift values mirror Kotlin; Kotlin wins.
- No instrumented tests, no XCUITest (suspended by the owner). Gradle and `xcodebuild` only through `~/.claude/dev-team/bin/heavy`.
- Commits: Conventional Commits, `--no-verify` never, trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Stage explicit paths only.

## Review Focus

1. Reduce Motion on iOS: the press must not scale and the row tint must still appear, instantly (`.timingCurve(..., duration: 0)` must not leave the view stuck mid-state). Pinned by the `reduced` value tests in Tasks 1–2 plus the manual check in Task 3.
2. Releasing a press mid-way glides back from where it is with no overshoot (tween, not spring). Manual check in Task 2.
3. `AppValueRow` inside a grouped `List` (Settings): the tint covers the row content, not the List's side insets. Accept unless it looks broken; then fall back to the native List highlight (Task 3, step 4).
4. A disabled primary button still shows 0.4 opacity and never scales (SwiftUI does not report `isPressed` for disabled buttons; verify once in Task 2).
5. Material-pack apps (showcase) see no change: pinned by the Material assertions in Tasks 1–2.

---

### Task 1: Kotlin motion tokens

**Files:**
- Modify: `core/designsystem/src/main/kotlin/com/whitelabel/core/designsystem/theme/Motion.kt`
- Modify: `core/designsystem/src/test/kotlin/com/whitelabel/core/designsystem/theme/StylePackTest.kt`
- Modify: `docs/branding.md:111` (Gallery motion cell), `docs/adding-a-shared-feature.md:98` (list `press` among the named curves)

**Interfaces:**
- Produces: `MotionSpec.press: MotionCurve` and `MotionSpec.pressScale: Float`, declared after `snackbar`, no defaults. Values per Global Constraints for `Material`, `Gallery`, `Reduced`. New private constant `EaseOut = CubicBezier(0.32f, 0.72f, 0f, 1f)` in the companion.

- [ ] **Step 1: Write the failing tests** in `StylePackTest.kt`

```kotlin
// in `gallery matches the design`: replace the tabSwitch / tabSwitchScale lines
assertThat(g.motion.tabSwitch).isEqualTo(Tween(150, standard))
assertThat(g.motion.tabSwitchScale).isEqualTo(1f)
assertThat(g.motion.press).isEqualTo(Tween(120, CubicBezier(0.32f, 0.72f, 0f, 1f)))
assertThat(g.motion.pressScale).isEqualTo(0.97f)

// in `material reproduces today`
assertThat(m.motion.press).isEqualTo(Spring(dampingRatio = 1f, stiffness = 400f))
assertThat(m.motion.pressScale).isEqualTo(0.98f)

// new test
@Test fun `reduced press is instant and never scales`() {
    for (pack in listOf(StylePack.Material, StylePack.Gallery)) {
        val reduced = pack.reduced().motion
        assertThat(reduced.press).isEqualTo(Tween(0, CubicBezier(0f, 0f, 1f, 1f)))
        assertThat(reduced.pressScale).isEqualTo(1f)
    }
}
```

`curves()` stays without `press`, so `reduced collapses every curve to a 150 ms linear crossfade` keeps pinning the navigation crossfade.

- [ ] **Step 2: Run, expect FAIL** (unresolved `press`/`pressScale`)

Run: `~/.claude/dev-team/bin/heavy ./gradlew :core:designsystem:test --tests '*StylePackTest*'`

- [ ] **Step 3: Implement** the two fields and values in `Motion.kt`. Gallery `tabSwitch = Tween(150, Standard)`, `tabSwitchScale = 1f`. `Reduced`: pass `press = Tween(0, Linear)`, `pressScale = 1f` by name. Update the KDoc: `press`/`pressScale` are button and chip presses (Android buttons keep the Material ripple; apps may use them for their own pressable views), and Gallery's tab switch is a plain crossfade.

- [ ] **Step 4: Run, expect PASS** (same command).

- [ ] **Step 5: Docs.** `branding.md` Gallery motion cell: "420 ms pushes and shared elements, 320 ms back, a 150 ms crossfade between tabs, a 120 ms press to 0.97 (iOS buttons)". `adding-a-shared-feature.md:98`: add `press` to the named curves.

- [ ] **Step 6: Commit**

```bash
git add core/designsystem/src/main/kotlin/com/whitelabel/core/designsystem/theme/Motion.kt \
  core/designsystem/src/test/kotlin/com/whitelabel/core/designsystem/theme/StylePackTest.kt \
  docs/branding.md docs/adding-a-shared-feature.md
git commit -m "feat(designsystem): add press motion and calm the gallery tab switch"
```

### Task 2: Swift tokens and a steady button press

**Files:**
- Modify: `ios/WhitelabelKit/Sources/WhitelabelKit/DesignSystem/Motion.swift`
- Modify: `ios/WhitelabelKit/Sources/WhitelabelKit/DesignSystem/Components/AppButtonStyles.swift:81-86`
- Modify: `ios/WhitelabelKit/Tests/WhitelabelKitTests/StylePackTests.swift`

**Interfaces:**
- Consumes: Task 1's values.
- Produces: `MotionSpec.press: MotionCurve`, `MotionSpec.pressScale: Double` (public, memberwise order matches Kotlin), private `easeOut = CubicBezier(0.32, 0.72, 0, 1)`.

- [ ] **Step 1: Write the failing tests** in `StylePackTests.swift`

```swift
// testGalleryMatchesKotlin: replace the tabSwitch / tabSwitchScale lines
XCTAssertEqual(gallery.motion.tabSwitch, .tween(milliseconds: 150, CubicBezier(0.2, 0, 0, 1)))
XCTAssertEqual(gallery.motion.tabSwitchScale, 1)
XCTAssertEqual(gallery.motion.press, .tween(milliseconds: 120, CubicBezier(0.32, 0.72, 0, 1)))
XCTAssertEqual(gallery.motion.pressScale, 0.97)

// testMaterialMatchesKotlin
XCTAssertEqual(material.motion.press, .spring(dampingRatio: 1, stiffness: 400))
XCTAssertEqual(material.motion.pressScale, 0.98)

// testReducedMotionUsesThePacksReducedValues
XCTAssertEqual(reduced.press, .tween(milliseconds: 0, CubicBezier(0, 0, 1, 1)))
XCTAssertEqual(reduced.pressScale, 1)
```

- [ ] **Step 2: Run, expect FAIL** (compile error on `press`)

Run: `~/.claude/dev-team/bin/heavy scripts/check-ios.sh` (or, faster, the same `xcodebuild … test` line with `-only-testing:WhitelabelKitTests/StylePackTests`).

- [ ] **Step 3: Implement** the mirror in `Motion.swift` (comments naming the Kotlin source, as today). Then `pressEffect`:
  `scaleEffect(pressed ? style.pack.motion.pressScale : 1)`, keep `.opacity(pressed ? 0.9 : 1)`, `.animation(style.pack.motion.press.animation, value: pressed)`. Drop the `motion != .reduced` check (the reduced pack's `pressScale` is 1). Doc comment: "The pack's press: Gallery scales to 0.97 in 120 ms with no bounce; Reduce Motion: no scale, instant."

- [ ] **Step 4: Run, expect PASS** (same command). The existing `ComponentTests` must stay green.

- [ ] **Step 5: Manual check** in the `Buttons` preview (Xcode canvas or Showcase in the simulator with the Gallery pack): press and release mid-way, no wobble; with Settings ▸ Accessibility ▸ Reduce Motion on, no scale; disabled button never scales.

- [ ] **Step 6: Commit**

```bash
git add ios/WhitelabelKit/Sources/WhitelabelKit/DesignSystem/Motion.swift \
  ios/WhitelabelKit/Sources/WhitelabelKit/DesignSystem/Components/AppButtonStyles.swift \
  ios/WhitelabelKit/Tests/WhitelabelKitTests/StylePackTests.swift
git commit -m "feat(ios): press buttons on the pack's calm press curve"
```

### Task 3: iOS rows tint on press

**Files:**
- Modify: `ios/WhitelabelKit/Sources/WhitelabelKit/DesignSystem/Components/AppButtonStyles.swift` (add the style next to the others)
- Modify: `ios/WhitelabelKit/Sources/WhitelabelKit/DesignSystem/Components/AppListRows.swift:21` (`.buttonStyle(.plain)` → `.buttonStyle(RowButtonStyle())`)
- Modify: `ios/WhitelabelKit/Sources/WhitelabelKit/DesignSystem/DesignSystemCatalog.swift` and the `Buttons` `#Preview` (show a pressable row)
- Modify: `docs/ios.md` (one line under Packs: `RowButtonStyle` for full-width tappable rows; Munch's `CreatureRow` adopts it in the bump PR)

**Interfaces:**
- Consumes: `MotionSpec.press` (Task 2).
- Produces: `public struct RowButtonStyle: ButtonStyle` with `public init()`. Body: `configuration.label` with `.background(style.colors.onSurface.opacity(configuration.isPressed ? 0.1 : 0))` (Material's 10 % pressed state layer), `.contentShape(Rectangle())`, `.animation(style.pack.motion.press.animation, value: configuration.isPressed)`. No scale, never below the label's own frame.

There is no testable logic here (the pressed state can't be rendered by `ImageRenderer`), so this task is checked by eye.

- [ ] **Step 1: Implement** `RowButtonStyle` and switch `AppValueRow` to it.
- [ ] **Step 2: Build and run the Swift tests** (`~/.claude/dev-team/bin/heavy scripts/check-ios.sh`): all green.
- [ ] **Step 3: Manual check** in the Showcase settings screen and the catalog, Gallery and Material packs, light and dark: pressing a value row tints it; releasing clears it; Reduce Motion on: the tint still shows, instantly.
- [ ] **Step 4: If the tint inside the Settings `List` looks clipped by the side insets** (Review Focus 3), keep `RowButtonStyle` public for rows outside lists, and give `AppValueRow` the native List highlight instead (`.buttonStyle(.automatic)` inside a `List`). Note the outcome in the PR.
- [ ] **Step 5: Commit**

```bash
git add ios/WhitelabelKit/Sources/WhitelabelKit/DesignSystem/Components/AppButtonStyles.swift \
  ios/WhitelabelKit/Sources/WhitelabelKit/DesignSystem/Components/AppListRows.swift \
  ios/WhitelabelKit/Sources/WhitelabelKit/DesignSystem/DesignSystemCatalog.swift docs/ios.md
git commit -m "feat(ios): tint rows on press with RowButtonStyle"
```

### Finish

- [ ] `~/.claude/dev-team/bin/heavy scripts/check.sh` and `~/.claude/dev-team/bin/heavy scripts/check-ios.sh` green (UI tests skipped as configured).
- [ ] Push and open the foundation PR from the template; the owner merges. Then Munch: `chore(deps): bump foundation to <sha>`, move Munch's local press/crossfade tokens onto `motion.press`/`pressScale`, and put `CreatureRow` and the `HomeScreen.swift:206,220` rows on `RowButtonStyle`.
