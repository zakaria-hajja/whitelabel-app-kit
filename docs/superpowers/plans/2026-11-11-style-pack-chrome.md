# Style Pack Screen Chrome Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The style pack decides the top bar, section headers and value-row layout. The shared Settings screen and `AppScaffold` follow it on Android and iOS, so Munch's Settings matches its design. Then Munch adopts it.

**Architecture:** The new pack fields go in `StylePack` (Kotlin), mirrored in Swift's `StylePack`. They are read by:
- `AppScaffold`;
- a new `AppSectionHeader`;
- `SettingsValueRow` / `AppValueRow`;
- a new `SettingsLinkRow`.

The shared Settings screens use these. Material values reproduce today exactly.

**Tech Stack:** Kotlin, Compose Material 3, Compose UI tests; Swift, SwiftUI, XCTest.

**Spec:** `docs/superpowers/specs/2026-11-11-style-pack-chrome-design.md`.

**Branches:**
- Foundation: `feat/style-pack-chrome` (worktree `/Users/zakariahajja/Whitelabel/android-starter-kit-chrome`, from `origin/main` 0be2ac2), Tasks 1–5.
- Munch: `feat/gallery-settings` (worktree `/Users/zakariahajja/Whitelabel/munch-gallery`, from Munch `origin/main` after foundation PR merge), Task 6. **Task 6 starts only after the owner merges the foundation PR.**

## Global Constraints

- **Material-pack apps don't change.** `StylePack.Material` gets `Raised` / `Accent` / `Inline`, and every existing showcase and settings test passes unchanged.
- **Gallery gets the design's look:**
  - `Flat` top bar: page background, no elevation or scroll tint, title in `headlineSmall`;
  - `Caption` headers: `labelSmall`, uppercase, letter-spacing 0.12 em, `onSurfaceVariant`;
  - `Stacked` rows: label, then the value under it in `onSurfaceVariant`.
- **Side padding:** rows and headers use `AppTheme.style.sizing.screenGutter` (iOS: `style.pack.sizing.screenGutter`), never a fixed 16.
- **Accessibility:** a value row stays one button reading "Label, Value"; a section header is a heading; link rows are at least `listRowMinHeight` tall.
- **About line:** "<appName> <version>" plus " · <note>" when `aboutNote` is set.
- **Swift mirrors Kotlin** (Kotlin wins), and `StylePackTests` pins it.
- **Git:** Conventional Commits with the Claude trailer and explicit-path staging. Run `scripts/check.sh` before pushing; `scripts/check-ios.sh` for the iOS task. Device tests run on `ANDROID_SERIAL=emulator-5554`. Push and open a PR at the end of each branch; the owner merges.

## Review Focus

- **A long value under Gallery** ("3 days before", a long language name) wraps under the label and never overlaps it. Test: Task 3 `aLongStackedValueWraps`.
- **Dark theme:** the flat top bar uses the dark background, not the light one. Test: Task 2 `flatTopBarMatchesTheBackgroundInDark`.
- **TalkBack on a stacked row** still reads one button "Theme, System default". Test: Task 3 `stackedRowIsOneButton`.
- **An app section that does draw something** (Munch's blocked callout) keeps its divider, while an empty one draws none. Test: Task 3 `onlyEmptySectionsVanish`.
- **Showcase (Material) Settings looks the same:** inline rows, accent headers, raised bar. Tests: Task 2 `materialKeepsTheRaisedBar` and Task 3 `materialKeepsInlineRowsAndAccentHeaders`.

---

### Task 1: Pack fields (Kotlin)

**Files:**
- Modify: `core/designsystem/src/main/kotlin/com/whitelabel/core/designsystem/theme/StylePack.kt`.
- Test: `core/designsystem/src/test/kotlin/com/whitelabel/core/designsystem/theme/StylePackTest.kt`.

**Interfaces (produced):**
```kotlin
enum class TopBarStyle { Raised, Flat }
enum class SectionHeaderStyle { Accent, Caption }
enum class ValueRowLayout { Inline, Stacked }
// StylePack += val topBar: TopBarStyle, val sectionHeader: SectionHeaderStyle, val valueRows: ValueRowLayout
// Material: Raised, Accent, Inline. Gallery: Flat, Caption, Stacked.
```

- [ ] **Step 1: Write the failing tests** in `StylePackTest`:
  - `material keeps today's chrome`: `Raised`, `Accent`, `Inline`;
  - `gallery chrome follows the design`: `Flat`, `Caption`, `Stacked`.
- [ ] **Step 2: Run** `./gradlew :core:designsystem:testDebugUnitTest`. Expected: FAIL (compilation).
- [ ] **Step 3: Implement.** Document each enum value in one line.
- [ ] **Step 4: Run** the same command, then `./gradlew assembleDebug`. Expected: PASS. Every `StylePack(...)` construction in the repo compiles; add the fields where `copy` isn't used.
- [ ] **Step 5: Commit** `feat(designsystem): let the style pack choose top bar, headers and rows`.

### Task 2: `AppScaffold` top bar and `AppSectionHeader`

**Files:**
- Modify: `core/designsystem/.../component/AppScaffold.kt`.
- Create: `core/designsystem/.../component/AppSectionHeader.kt`.
- Test: `core/designsystem/src/androidTest/.../ComponentsTest.kt`.

**Interfaces (produced):**
```kotlin
@Composable fun AppSectionHeader(text: String, modifier: Modifier = Modifier)   // padding: horizontal screenGutter, top spacing.lg, bottom spacing.sm
```
- **`Flat` bar:** `TopAppBar` with `containerColor = scrolledContainerColor = colorScheme.background`, title `headlineSmall`, test tag `AppTopBar`.
- **`Raised` bar:** today's code path, unchanged, with the same tag.
- **`AppSectionHeader`:**
  - `Caption` is `text.uppercase()` in `labelSmall` with `letterSpacing = 0.12.em` and `onSurfaceVariant`;
  - `Accent` is today's settings header (`titleSmall`, primary);
  - both have `semantics { heading() }`.

- [ ] **Step 1: Write the failing tests** (`ComponentsTest`):
  - `sectionHeaderIsUppercaseUnderGallery`: under `AppTheme(stylePack = StylePack.Gallery)`, `AppSectionHeader("Appearance")` → a node with text "APPEARANCE" that is a heading.
  - `sectionHeaderKeepsItsCaseUnderMaterial`: "Appearance".
  - `flatTopBarMatchesTheBackground`: Gallery `AppScaffold(title = "T") {}` → the pixel at the centre of `onNodeWithTag("AppTopBar").captureToImage()` equals `MaterialTheme.colorScheme.background`, read inside the content and stored in a local.
  - `flatTopBarMatchesTheBackgroundInDark`: the same with `darkTheme = true`.
  - `materialKeepsTheRaisedBar`: under Material, the pixel equals `colorScheme.surface`.
- [ ] **Step 2: Run** `ANDROID_SERIAL=emulator-5554 ./gradlew :core:designsystem:connectedDebugAndroidTest`. Expected: FAIL (no `AppSectionHeader`, no tag).
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** the same command. Expected: PASS.
- [ ] **Step 5: Commit** `feat(designsystem): draw top bars and section headers per the pack`.

### Task 3: Shared Settings follows the pack (Android)

**Files:**
- Modify: `feature/settings/src/main/kotlin/com/whitelabel/feature/settings/SettingsRows.kt` (`SettingsValueRow` per `valueRows`; new `SettingsLinkRow`).
- Modify: `feature/settings/.../presentation/SettingsScreen.kt`.
- Test: `feature/settings/src/androidTest/.../SettingsScreenTest.kt`.

**Interfaces (produced):**
```kotlin
@Composable fun SettingsLinkRow(label: String, onClick: () -> Unit, modifier: Modifier = Modifier)
// full width, min height listRowMinHeight, horizontal padding screenGutter, bodyLarge, Role.Button
```
- **`SettingsValueRow` under `Stacked`:** a `Column` with the label (`bodyLarge`) and the value (`bodyMedium`, `onSurfaceVariant`). Same merged semantics and `Role.Button`. Min height `listRowMinHeight`.
- **`Inline`:** unchanged, but the padding comes from `screenGutter`.
- **Section:**
  - The title uses `AppSectionHeader`.
  - Dividers get the test tag `SettingsDivider`.
  - An untitled section whose content measures 0 height places nothing, so no divider. Use a `Layout` that measures the content first.
- **About:** one muted `bodyMedium` line, `"${state.appName} ${state.versionName}"` + `" · " + note` when `aboutNote` is set. New string `settings_about_line` = "%1$s %2$s", in `values` and `values-fr`. The links become `SettingsLinkRow`s.
- The old `settings_version` string goes if it becomes unused; lint checks this.

- [ ] **Step 1: Write the failing tests** (`SettingsScreenTest`; a helper to show under a given pack):
  - `valueRowStacksUnderGallery`: the "System default" node's top ≥ the "Theme" node's bottom.
  - `materialKeepsInlineRowsAndAccentHeaders`: under Material, "System default" top < "Theme" bottom, and the header text is "Appearance" (not uppercase).
  - `stackedRowIsOneButton`: under Gallery, the merged node with text "Theme" has role Button and contains "System default".
  - `aLongStackedValueWraps`: `SettingsValueRow("Default reminder", "A very long value that cannot fit on one line at all", {})` under Gallery → the value's left equals the label's left, and its top ≥ the label's bottom.
  - `onlyEmptySectionsVanish`: sections `[SettingsSection(null) {}, SettingsSection(null) { Text("Blocked") }]` → `onAllNodesWithTag("SettingsDivider")` has 1 more node than without the second section.
  - `aboutIsOneLine`: `aboutNote = R.string.settings_retry` and app name "Showcase" (the test state's) → a node "Showcase 1.0.0 · Retry".
  - `linksAreRowsAtTheGutter`: under Gallery, the "Privacy policy" node's left == 20.dp, and it has role Button.
- [ ] **Step 2: Run** `ANDROID_SERIAL=emulator-5554 ./gradlew :feature:settings:connectedDebugAndroidTest`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** the same command and `scripts/check.sh`. Expected: PASS. Every existing settings test still passes.
- [ ] **Step 5: Commit** `feat(settings): follow the style pack for headers, rows and about`.

### Task 4: iOS mirrors the pack

**Files:**
- Modify: `ios/WhitelabelKit/Sources/WhitelabelKit/DesignSystem/StylePack.swift` (the three enums and fields).
- Modify: `.../Components/AppListRows.swift` (`AppValueRow` per `valueRows`).
- Create: `.../Components/AppSectionHeader.swift`.
- Modify: `.../Features/Settings/SettingsScreen.swift` (the headers via `AppSectionHeader`, the About line, link rows at the gutter) and `PackPreviews.swift` if it lists fields.
- Modify: `ios/WhitelabelKit/Resources/WhitelabelKit.xcstrings` ("%@ %@" about line, if strings are keyed).
- Test: `ios/WhitelabelKit/Tests/WhitelabelKitTests/StylePackTests.swift`.

**Interfaces (produced):** `public enum TopBarStyle { case raised, flat }`, `SectionHeaderStyle { case accent, caption }`, `ValueRowLayout { case inline, stacked }`. `StylePack.topBar`, `.sectionHeader` and `.valueRows`, with the Kotlin values. `public struct AppSectionHeader: View { init(_ text: String) }` (caption: uppercased, `.caption` font, tracking 0.12 em, `onSurfaceVariant`; `.accessibilityAddTraits(.isHeader)`).
- **Flat top bar on iOS:** `toolbarBackground(style.colors.background, for: .navigationBar)` on the settings screen when `topBar == .flat`.
- **`AppValueRow` under `.stacked`:** a `VStack(alignment: .leading)` with the label, then the value. Keep the chevron and the accessibility label/value.

- [ ] **Step 1: Write the failing tests:** `StylePackTests.testGalleryMatchesKotlin` and `testMaterialMatchesKotlin` gain asserts for the three fields.
- [ ] **Step 2: Run** `scripts/check-ios.sh`. Expected: FAIL (compilation).
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `scripts/check-ios.sh`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(ios): mirror the pack's top bar, headers and rows`.

### Task 5: Docs, showcase check and PR (foundation)

**Files:** Modify `docs/branding.md` or wherever style packs are documented (`grep -rn "titlesUseBodyFont" docs`), and `docs/shared-features.md` (Settings: `AppSectionHeader` and `SettingsLinkRow` for app sections, and the About line).

- [ ] **Step 1: Document** the three fields with their two values, and when an app screen should use `AppSectionHeader`.
- [ ] **Step 2: Final verification.**
  - `scripts/check.sh` passes.
  - `scripts/check-ios.sh` passes.
  - `ANDROID_SERIAL=emulator-5554 ./gradlew :apps:showcase:connectedDebugAndroidTest :feature:settings:connectedDebugAndroidTest :core:designsystem:connectedDebugAndroidTest` passes.
  - `./gradlew assembleDebug` builds.
- [ ] **Step 3: Commit** `docs: describe the pack's top bar, headers and rows`. Push and open the PR.

### Task 6 (Munch, after the foundation PR merges): adopt the Gallery chrome

**Files:**
- Modify: the `foundation` pointer.
- Modify: `munch/rewards/src/main/kotlin/com/mobileminds/munch/rewards/RewardsScreen.kt` (section titles via `AppSectionHeader`, the "Rewards" title in `headlineSmall` as today).
- Modify: `munch/settings/src/main/kotlin/com/mobileminds/munch/settings/MunchSettingsSections.kt`, if its callout section still pads itself.
- Modify: `munch/rewards/src/androidTest/.../RewardsScreenTest.kt` and `app/src/androidTest/.../SettingsFlowTest.kt` (header texts are now uppercase where tests read them).

- [ ] **Step 1: Write the failing test:** `RewardsScreenTest.sectionTitlesAreCaptions` → nodes "AD-FREE TIME", "BADGES", "RECENT" exist and are headings.
- [ ] **Step 2: Run** `ANDROID_SERIAL=emulator-5554 ./gradlew :munch:rewards:connectedDebugAndroidTest`. Expected: FAIL.
- [ ] **Step 3: Implement.** Bump the foundation in the same branch, as its own `chore(deps): bump foundation to <sha>` commit.
- [ ] **Step 4: Run** `scripts/check.sh` and all three device suites (app, creatures, rewards). Expected: PASS. Then screenshot Settings and Rewards on the emulator and compare with `docs/design` (top bar on the background with a serif title, uppercase caption headers, stacked rows, no stray divider, one About line).
- [ ] **Step 5: Commit** `feat(rewards): use the gallery section headers`. Push and open the PR.
