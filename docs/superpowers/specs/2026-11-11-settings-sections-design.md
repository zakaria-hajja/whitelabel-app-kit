# Settings sections — short design

**Status:** approved by the owner on 2026-11-11. **Branch:** `feat/settings-sections`.

## Intent

Every app built on the foundation needs a settings screen. Most of it is shared (theme, language,
about, legal links, diagnostics, sign out), and some of it is the app's own (Munch has currency,
default reminder days, reminder time and a "Reminders are blocked" callout). Today `:feature:settings`
has no way to add rows, so each app would copy the shared parts. This change lets an app add its own
sections to the shared screen.

- **Goals:** an app adds sections above the shared ones; its rows look like the shared rows; Munch
  can build the Settings screen from its design on top of this.
- **Non-goals:** reordering or removing the shared sections; settings search; nested settings pages.
- **Anti-goals:** any app identity in `:feature:settings`; a second settings screen per app.

## Approaches

1. **App-only screen** (each app builds its own settings in its own module). Fastest for Munch, but
   every app re-implements theme, language, about and diagnostics.
2. **Shared screen with app sections (chosen).** One small foundation branch; each app then writes
   only its own rows.

The owner picked 2: every app built on the foundation will want settings, and the shared parts exist.

## Design

- `SettingsOptions(sections: List<SettingsSection> = emptyList(), aboutNote: Int? = null, …)`
  (existing fields unchanged).
  - `SettingsSection(title: Int?, content: @Composable () -> Unit)`: rendered in list order **above**
    the shared sections. A null title draws no header (used for callouts). The content may call
    `hiltViewModel()` for its own state.
  - `aboutNote`: an optional string shown under the version in About ("Your data stays on this phone.").
- Public building blocks in `:feature:settings` so app rows match the shared ones:
  - `SettingsValueRow(label: String, value: String, onClick: () -> Unit)`: a label with its current
    value on one row, at least 48 dp tall, the whole row clickable with role Button.
  - `SettingsChoiceSheet(title: String, choices: List<T>, selected: T, label: (T) -> String,
    onSelect: (T) -> Unit, onDismiss: () -> Unit)`: a modal bottom sheet with one radio row per choice
    and a **Done** button. Selecting applies at once (no Save), as Munch's design says.
- The shared theme and language sections become value rows opening the choice sheet, instead of
  always-open radio lists. This changes showcase and globex too (owner's call accepted: matches the
  approved Munch design and is more compact).

## Left to the builder

The sheet's exact layout within the design system; the section header style (kept as today's
`Section`); test names.
