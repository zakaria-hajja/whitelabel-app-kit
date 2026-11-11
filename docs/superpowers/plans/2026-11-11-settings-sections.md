# Settings Sections Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Apps add their own sections to the shared settings screen, built from public rows that match the shared ones.

**Architecture:** `:feature:settings` gains two public building blocks: `SettingsValueRow` and `SettingsChoiceSheet`. The shared theme and language sections are rebuilt from them. `SettingsOptions` gains `sections` (shown above the shared sections) and `aboutNote`. Which sheet is open is screen-local UI state; no ViewModel change.

**Tech Stack:** Kotlin, Compose Material 3 (`ModalBottomSheet`), Compose UI tests (instrumented).

**Spec:** `docs/superpowers/specs/2026-11-11-settings-sections-design.md`. The consumer is Munch's Settings tab (Munch spec `2026-11-11-munch-reminders-design.md`): a callout section without a title, then Currency, Default reminder and Reminder time value rows, then the shared theme and about with `aboutNote`, and no language.

**Branch:** `feat/settings-sections` from `origin/main` (c0060c4), worktree `../android-starter-kit-settings`.

## Global Constraints

- The existing `SettingsOptions` fields and defaults are unchanged; new fields default to "nothing added", so `settings()` with no arguments only changes how theme and language look.
- Selecting a choice applies at once (no Save); **Done** closes the sheet.
- Rows are at least 48 dp tall and whole-row clickable.
- No app identity in `:feature:settings`; shared modules never depend on apps.
- Conventional Commits with the Claude trailer, explicit-path staging, and `scripts/check.sh` before pushing. Instrumented tests run on `ANDROID_SERIAL=emulator-5554`.

## Review Focus

- **Rotating the phone with a sheet open:** the sheet stays open on the same list. (Task 1: `rememberSaveable` for the open sheet; test `openSheet_survivesRecreation`.)
- **Back while a sheet is open:** the sheet closes and the screen stays. (Task 1: `onDismissRequest` → `onDismiss`.)
- **TalkBack:** a value row reads as one button ("Theme, System default"), and the sheet's choices are a radio group. (Task 1: merged semantics, `selectableGroup`; test `valueRow_isOneButton`.)
- **A long value or label** (a German language name, "3 days before") wraps instead of overlapping. (Task 1: the label takes `weight(1f)`, the value wraps to the end.)
- **An app section that uses `hiltViewModel()`:** it works because sections are composed inside the settings destination. (Task 2 docs; Munch's branch exercises it.)

---

### Task 1: Value rows and the choice sheet; theme and language use them

**Files:**
- Create: `feature/settings/src/main/kotlin/com/whitelabel/feature/settings/SettingsRows.kt` (public `SettingsValueRow`, `SettingsChoiceSheet`).
- Modify: `feature/settings/src/main/kotlin/com/whitelabel/feature/settings/presentation/SettingsScreen.kt` (theme and language sections; remove the now-unused `ChoiceRow`).
- Modify: `feature/settings/src/main/res/values/strings.xml` (`settings_done` = "Done").
- Test: `feature/settings/src/androidTest/kotlin/com/whitelabel/feature/settings/SettingsScreenTest.kt`.

**Interfaces (produced):**
```kotlin
@Composable fun SettingsValueRow(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier)
@Composable fun <T> SettingsChoiceSheet(
    title: String, choices: List<T>, selected: T, label: @Composable (T) -> String,
    onSelect: (T) -> Unit, onDismiss: () -> Unit,
)
```
- `SettingsValueRow`: the label on the start (`weight(1f)`), the value at the end in `onSurfaceVariant`, at least 48 dp tall, `clickable(role = Role.Button)` with merged semantics.
- `SettingsChoiceSheet`: a `ModalBottomSheet` with the title, one radio row per choice in a `selectableGroup` (tapping calls `onSelect`; it doesn't close), and a **Done** button calling `onDismiss`. `onDismissRequest` (back, scrim, swipe) also calls `onDismiss`.
- The screen keeps `var openSheet by rememberSaveable { mutableStateOf<String?>(null) }` with the values `"theme"` and `"language"`.
- The theme section becomes one value row (label "Theme", value = the current theme's label). The language section becomes one value row (label "Language", value = "Device language" or the language's own name). Each opens its sheet.

**Steps:**
- [ ] **Step 1: Write the failing tests.** Replace `selectDarkTheme_sendsThemeAction`, keep the other tests unchanged, and add:
  - `selectDarkTheme_sendsThemeAction`: state theme SYSTEM → click the "Theme" row → "Dark" is shown in the sheet → click "Dark" → actions == `[OnThemeSelect(DARK)]`; "Done" still exists (the sheet stays open until Done).
  - `themeRow_showsCurrentValue`: state theme DARK → a node with text "Dark" exists without opening anything, and "Light" doesn't exist.
  - `done_closesTheSheet`: open the theme sheet → click "Done" → "Light" doesn't exist.
  - `languageRow_opensLanguageChoices`: state `remoteLanguages = null`, options `languages = listOf("en", "fr")` → click "Language" → click "Français" → actions == `[OnLanguageSelect("fr")]`.
  - `valueRow_isOneButton`: the node with text "Theme" (`useUnmergedTree = false`) has role Button and its text includes "System default".
  - `openSheet_survivesRecreation`: use `StateRestorationTester` → open the theme sheet → `emulateSavedInstanceStateRestore()` → "Light" exists.
- [ ] **Step 2: Run** `ANDROID_SERIAL=emulator-5554 ./gradlew :feature:settings:connectedDebugAndroidTest`. Expected: FAIL (no "Theme" row opens a sheet; the radio list shows "Light" always).
- [ ] **Step 3: Implement** as in the Interfaces block.
- [ ] **Step 4: Run** the same command. Expected: PASS. Then `./gradlew :feature:settings:testDebugUnitTest`. Expected: PASS (the ViewModel is unchanged).
- [ ] **Step 5: Commit** `feat(settings): show theme and language as value rows with a sheet`

### Task 2: App sections and the about note

**Files:**
- Modify: `feature/settings/src/main/kotlin/com/whitelabel/feature/settings/Settings.kt` (`SettingsOptions.sections`, `aboutNote`, `SettingsSection`).
- Modify: `SettingsScreen.kt` (render the sections first; `aboutNote` under the version).
- Modify: `docs/shared-features.md` (Settings: app sections, the two building blocks, and an example).
- Test: `SettingsScreenTest.kt`.

**Interfaces:**
- Consumes: `SettingsValueRow` and `SettingsChoiceSheet` (Task 1).
- Produces:
```kotlin
data class SettingsOptions(
    val showNotifications: Boolean = true,
    val languages: List<String> = listOf("en", "fr"),
    val sections: List<SettingsSection> = emptyList(),
    @StringRes val aboutNote: Int? = null,
)
/** Shown above the shared sections, in order. A null [title] draws no header (for callouts). */
class SettingsSection(@StringRes val title: Int?, val content: @Composable () -> Unit)
```
- A titled app section uses the same `Section` header and divider as the shared ones. An untitled one draws its content only, followed by the divider.

**Steps:**
- [ ] **Step 1: Write the failing tests.**
  - `appSection_rendersAboveTheme`: options `sections = listOf(SettingsSection(R.string.settings_account /* "Account": absent when signed out */) { SettingsValueRow("Currency", "Euro (€)", onClick = {}) })` → the "Currency" node's `top` < the "Theme" node's `top` (`getUnclippedBoundsInRoot()`).
  - `untitledSection_drawsNoHeader`: `SettingsSection(null) { Text("Reminders are blocked") }` → "Reminders are blocked" exists, and only one node has the "About" text (the shared one).
  - `aboutNote_showsUnderVersion`: `aboutNote = R.string.settings_retry` ("Retry", shown nowhere else when there is no error) → scroll to "Version 1.0.0"; the "Retry" node's top > the version's top.
  - `noSections_keepsTheSharedScreen`: default options → the first section header is "Theme" (the "Theme" node is the topmost text in the scrolling column).
- [ ] **Step 2: Run** `ANDROID_SERIAL=emulator-5554 ./gradlew :feature:settings:connectedDebugAndroidTest`. Expected: FAIL (does not compile: no `sections` or `SettingsSection`).
- [ ] **Step 3: Implement**, then document it in `docs/shared-features.md` under Settings: what a section is, that its content may call `hiltViewModel()`, the two building blocks, and an example that adds one value row with a sheet.
- [ ] **Step 4: Run** the same command and `scripts/check.sh`. Expected: PASS. Then `./gradlew assembleDebug`. Expected: showcase and globex build unchanged except for the theme and language look.
- [ ] **Step 5: Commit** `feat(settings): let apps add their own settings sections`
