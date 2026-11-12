# Style Pack Screen Chrome — Design

Status: approach A chosen by the owner (2026-11-11), awaiting approval of this doc. Branch
`feat/style-pack-chrome`. Consumer: Munch (Gallery pack). Visual source:
`munch/docs/design/src/pages/settings.html` and its `app.css` (`.topbar`, `.sec`, `.setting-row`).

## 1. Intent and the why

Munch's Settings tab looks like a different app from its own tabs. The shared screens get an app's
fonts, colors, corners, hairlines and spacing through the style pack, but three things a design
decides are hard-coded to Material: the top bar, section headers and list rows. Every app on the
Gallery pack hits this, on every shared screen. The fix belongs in the pack, so it reaches every
shared screen and every future app.

### Goals
- The style pack decides the top bar, section headers and value-row layout. Gallery gets Munch's
  design; Material stays exactly as today (showcase unchanged).
- The shared Settings screen (Android and iOS) and `AppScaffold` follow the pack. They also stop
  drawing what the design doesn't: a divider for an empty section, and link buttons indented
  differently from every other row.
- One public section-header component, so app screens (Munch's Rewards) match the shared ones.

### Non-goals
- New settings features (ad privacy choices, "Rate Munch": they come with the ads and release
  branches).
- Restyling individual shared feature screens beyond what `AppScaffold` and the settings rows give.

### Anti-goals
- Any visible change to Material-pack apps.
- Kotlin and Swift packs disagreeing (Kotlin wins; `StylePackTests` pins Swift to it).

## 2. Approaches

| | Approach | Trade-off |
|---|---|---|
| **A** | **The pack decides top bar, section headers and row layout; shared screens follow** | One change reaches every shared screen and app; touches the foundation on both platforms |
| B | Munch builds its own Settings screen on the shared ViewModel | Munch-only and faster, but duplicates the feature and leaves Diagnostics and other shared screens Material |

Owner picked **A**.

## 3. The pack

New `StylePack` fields (Kotlin, mirrored in Swift):

| Field | Material (today's look) | Gallery (Munch's design) |
|---|---|---|
| `topBar: TopBarStyle` | `Raised`: Material top app bar on the surface color, title in `titleLarge` | `Flat`: on the page background, no elevation or scroll tint, title in `headlineSmall` (the display font) |
| `sectionHeader: SectionHeaderStyle` | `Accent`: `titleSmall` in the primary color, as written | `Caption`: `labelSmall`, uppercase, letter-spacing 0.12 em, `onSurfaceVariant` |
| `valueRows: ValueRowLayout` | `Inline`: label on the start, value at the end | `Stacked`: label, value under it in `onSurfaceVariant` |

All rows and headers use `sizing.screenGutter` as their side padding (today some use a fixed 16 dp).

## 4. Components (`:core:designsystem`, and `WhitelabelKit` on iOS)

- `AppScaffold`: draws its top bar per `topBar`.
- New `AppSectionHeader(text)`: per `sectionHeader`, marked as a heading for accessibility.
- `SettingsValueRow` (and `AppValueRow` on iOS): per `valueRows`, still one button for TalkBack /
  VoiceOver ("Theme, System default").
- New `SettingsLinkRow(label, onClick)`: a full-width row at the gutter, at least `listRowMinHeight`
  tall, replacing the indented text buttons for Privacy, Terms, Contact support and Diagnostics.

## 5. The shared Settings screen (Android and iOS)

- Section titles use `AppSectionHeader`.
- An untitled section whose content draws nothing takes no space and draws no divider (Munch's
  "notifications blocked" callout section when notifications aren't blocked).
- About: the version and the app's `aboutNote` read as one muted line,
  "Munch 0.1.0 · Your data stays on this phone." (the app name and version from `BrandConfig`; no
  note: "Munch 0.1.0"). Links follow as `SettingsLinkRow`s.

## 6. Munch (its own PR after this merges)

Bump the foundation, use `AppSectionHeader` for the Rewards tab's section titles, and check
Settings against the design.

## 7. Testing

- Kotlin: a unit test pinning Gallery's and Material's new fields; Compose tests for
  `AppSectionHeader` (uppercase under Gallery, unchanged under Material), `SettingsValueRow` stacked
  vs inline (the value's position relative to the label), the empty-section rule and the About line.
  Showcase's existing settings and e2e tests stay green.
- Swift: `StylePackTests` gains the new fields for both packs; the iOS check script stays green.

## 8. Left to the builder

- Exact Compose / SwiftUI implementation of the flat top bar and the empty-section rule.
- Whether `SettingsLinkRow` shows a trailing chevron (the design's rows don't).
