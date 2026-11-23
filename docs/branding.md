# Branding an app

An app's look comes entirely from resources in `apps/<name>/src/main/res`. `:core:designsystem`
and `:core:shell` declare every brand resource with a default. Android resource merging lets the
app override any of them by name, so no Kotlin changes and no feature module knows which app it
is in.

## Required (the build fails without them)

| Resource | Purpose |
|---|---|
| `mipmap-anydpi-v26/ic_launcher.xml`, `ic_launcher_round.xml` | Adaptive launcher icon: background, foreground and monochrome layers |
| `drawable/ic_launcher_foreground.xml` | Icon glyph, inside the 66dp safe zone of the 108dp canvas |
| `drawable/ic_launcher_monochrome.xml` | Themed-icon layer (Android 13+) |
| `drawable/brand_splash_icon.xml` | Splash-screen icon |
| `color/brand_splash_background`, `color/ic_launcher_background` | Splash and launcher backgrounds (declare them in any `values*/*.xml`) |

minSdk is 26, so vector drawables and adaptive icons cover every device; no PNG density
buckets are needed. Android Studio's *Image Asset* tool produces these files.

## Colors

Material 3 color roles, each with a light and a dark value:
`brand_light_<role>` and `brand_dark_<role>`. These are separate names, not `values-night`, so an
in-app light/dark choice works whatever the system setting. The roles are:

`primary on_primary primary_container on_primary_container secondary on_secondary
secondary_container on_secondary_container tertiary on_tertiary tertiary_container
on_tertiary_container error on_error background on_background surface on_surface
surface_variant on_surface_variant outline outline_variant surface_container_lowest
surface_container_low surface_container surface_container_high surface_container_highest
inverse_surface inverse_on_surface inverse_primary error_container on_error_container
surface_bright surface_dim primary_fixed primary_fixed_dim on_primary_fixed
on_primary_fixed_variant secondary_fixed secondary_fixed_dim on_secondary_fixed
on_secondary_fixed_variant tertiary_fixed tertiary_fixed_dim on_tertiary_fixed
on_tertiary_fixed_variant`

That is every color role of Material's `ColorScheme` except `surface_tint`, which follows
`primary`, and `scrim`, which stays black.

Where Material draws with the less obvious ones:

| Role | Drawn by |
|---|---|
| `surface_container` | the bottom bar, dropdown menus, the Material pack's scrolled top bar |
| `surface_container_low` | bottom sheets (the Settings pickers) |
| `surface_container_high` | dialogs (`AppConfirmDialog`) |
| `surface_container_highest` | a switch's unchecked track, filled cards (Material pack `AppCard`) |
| `surface_container_lowest` | nothing in the foundation yet; set it so the family stays complete |
| `outline_variant` | hairlines: the rule above the bottom bar and outlined cards (Gallery), dividers, filter-chip borders |
| `inverse_surface`, `inverse_on_surface`, `inverse_primary` | snackbars: container, text and action |
| `error_container`, `on_error_container` | error banners and validation summaries an app draws; nothing in the foundation yet |
| `surface_bright`, `surface_dim` | nothing in the foundation yet; Material keeps them for surfaces brighter or dimmer than `surface` |
| `*_fixed`, `*_fixed_dim`, `on_*_fixed`, `on_*_fixed_variant` | nothing in the foundation yet; they keep the same tone in light and dark, so set both themes to the same values |

- **Defaults:** any role you don't override keeps the Material 3 baseline value from
  `core/designsystem/src/main/res/values/brand.xml`. Those equal Material's own
  `lightColorScheme()`/`darkColorScheme()` (a test checks it), so an app looks exactly like
  Material wherever it sets nothing. The new roles stay Material's baseline (lilac surfaces, grey
  hairlines, dark snackbars) until you set them.
- **Override more than primary:** components such as the bottom-bar indicator use
  `secondary_container`. An app that only overrides the primary family, as showcase does
  today, shows the baseline lavender there. Override at least the primary, secondary and tertiary
  families for a fully branded look.
- **Contrast:** every `on_*` color must reach WCAG AA (4.5:1) against its base color, in both light
  and dark; so must `on_surface_variant` on `surface_container` (bar labels), `on_surface` on
  `surface_container_high` (dialogs), `inverse_on_surface` on `inverse_surface` (snackbars), and for
each fixed family `on_*_fixed` on `*_fixed` and `on_*_fixed_variant` on `*_fixed_dim`.
  `outline_variant` is a decorative hairline and is exempt. Write the ratios in a comment in your `brand.xml`.
  `contrastRatio()` in `:core:designsystem` computes them.
- **Dynamic color is off,** so the brand always applies.

## Shapes

`dimen/brand_corner_small`, `brand_corner_medium` and `brand_corner_large`. The defaults are 8, 12
and 16dp; 0, 2 and 4dp give a square look. A style pack that sets `corners` (Gallery
does) replaces these; see below.

Material 3 has two more steps. `brand_corner_extra_small` (snackbars, menus, tooltips) and
`brand_corner_extra_large` (dialogs, bottom sheets) default to Material's own 4 and 28dp, so an app
that doesn't set them looks as before. A pack's `Corners` has them as optional `extraSmall` and
`extraLarge`; `null` (the default, Gallery included) keeps Material's shape. To give dialogs and
sheets the pack's large radius, as design.html's Gallery does:

```kotlin
style(StylePack.Gallery.copy(corners = StylePack.Gallery.corners?.copy(extraLarge = 14.dp)))
```

Material's expressive steps (`largeIncreased`, `extraLargeIncreased`, `extraExtraLarge`) are
experimental and keep their defaults. On iOS, alerts are the system's; sheets take
`.presentationCornerRadius(style.pack.corners.large)` if an app wants them to match.

## Style packs (look and motion)

Colors and fonts come from resources (above). Everything else about the look is a **style pack**,
a `StylePack` from `:core:designsystem`, picked in the app definition:

```kotlin
appDefinition {
    style(StylePack.Gallery)
    // ...features
}
```

| Part | Material (default) | Gallery |
|---|---|---|
| Corners | `brand_corner_*` resources | 4 / 8 / 14 dp (extra small and extra large stay Material's 4 / 28 dp) |
| Surfaces | elevation shadows | 1 dp hairlines (cards outlined, a rule above the bottom bar) |
| Controls | 48 dp | 52 dp |
| Type | Material weights; titles in the display font | display and headlines at regular weight; titles in the body font |
| Motion | Navigation's default 700 ms fades | 420 ms pushes and shared elements, 320 ms back, a 150 ms crossfade between tabs, a 120 ms press to 0.97 (iOS buttons) |
| Haptics | none | a tick on confirm, a confirm on success, a toggle click |
| Top bar (`AppScaffold`) | Material's raised bar on the surface color | flat on the page background, title in the headline (display) font |
| Section headers (`AppSectionHeader`) | `titleSmall` in the primary color | a small uppercase caption, letter-spaced, muted |
| Value rows (`SettingsValueRow`) | label and value on one line | the value under the label, muted |

- An app that never calls `style()` gets `Material`, which is exactly the look apps had before packs.
- **Bottom bar on the page surface:** `StylePack.Gallery.copy(bottomBar = BottomBarStyle.Surface)`
  draws the bar on `surface`, under its `outline_variant` hairline, instead of on
  `surface_container`. The default is `BottomBarStyle.Container`. `Surface` needs
  `SurfaceStyle.Hairline` and is ignored on a Shadow pack, which needs the container color to set
  the bar apart.
- **Opt-in controls** (each default is Material's own look, so an app that doesn't set it renders
  as before; Android, since iOS already draws these the platform's way):
  - `buttons = ButtonStyle.Squared`: buttons take `shapes.small` corners and 600-weight labels; the
    outlined button gets a 1 dp `outline` border and a `primary` label (3:1 edge). Default `Pill`.
  - `bottomBarItems = BottomBarItemStyle.Squared`: the selected tab's 56 x 32 indicator takes
    `shapes.small` corners, and its label is `onSurface`. Default `Material`.
  - `selectionControls = SelectionControlStyle.Outline`: sheet drag handles and unselected radio
    buttons in `outline`. Shared sheets use it; an app's own sheets pass
    `dragHandle = { AppSheetDragHandle() }` and radios `colors = appRadioButtonColors()`. Default `Material`.
  - `valueRows = ValueRowLayout.Disclosure`: `SettingsValueRow` puts the value at the trailing edge
    (at most half the row) with a chevron on rows that open something, as iOS draws them.
  - `type = ….type.copy(labelWeight = FontWeight.SemiBold)`: `labelLarge` takes that weight, so
    everything styled `labelLarge` follows it: every Material button label, including raw
    `TextButton`s in dialogs, the date and time pickers' buttons and snackbar actions (which
    `ButtonStyle.Squared` doesn't reach); `DropdownMenuItem`s, extended FABs, segmented buttons and
    chips; the native-ad call to action; and any `Text(style = typography.labelLarge)` in an app.
    Default `null`: Material's 500.
  - `bottomBarLabelMaxFontScale = 1.5f`: bottom-bar labels stop growing at 150 % of their default
    size (at least 1; iOS tab labels don't scale at all). They still shrink to fit as below, never
    under their 100 % size. Default `null`: they follow the user's full font scale.
- **Opt-in chrome text** (a `TextSpec`: size, line height, weight, letter spacing and which brand
  font, `BrandFont.Body` by default; unset keeps today's role, so Munch and every app that doesn't
  set it render as before):
  - `sectionCaptionText`: the Caption section header's text, on every `AppSectionHeader` (the shared
    Settings sections and an app's own). Still uppercase and `onSurfaceVariant`. Unset:
    `labelSmall` (11 sp / 500) tracked 0.12 em on Android, the label role tracked 1.32 pt on iOS.
    Example, a 12/16 600 eyebrow: `sectionCaptionText = TextSpec(12.sp, 16.sp, FontWeight.SemiBold, letterSpacing = 0.6.sp)`.
    iOS has the same field (`TextSpec(size: 12, weight: .semibold, tracking: 0.6)`; no line height,
    as the header is one line); set it in the app's Swift pack too, so both platforms match.
  - `flatTopBarTitleText` (Android): the flat `AppScaffold` title, for example on Settings
    sub-pages. Unset: `headlineSmall`. iOS pages keep the system navigation title.
- Bottom-bar labels never break inside a word, on every pack: a label renders at the user's full font
  size (or the pack's cap) whenever it fits, may wrap between words as Material's does, and shrinks
  (with its line height) only as far as a word needs to fit its tab, never below its 100 % size.
- Vary a pack with `copy`: `StylePack.Gallery.copy(sizing = StylePack.Gallery.sizing.copy(controlHeight = 56.dp))`.
- With Android's "Remove animations" on, every transition becomes a 150 ms crossfade, and
  `AppTheme.reducedMotion` is true so features can stop their own ambient animations.
- The diagnostics catalog shows the active pack's name.
- An app's own screens use `AppSectionHeader` for their section titles, so they match the shared
  screens under any pack.

### Action dock (`AppActionScaffold` / `ActionScaffold`)

A full screen with a primary action keeps that action pinned at the bottom of the window, in an
**action dock**: outside the scroll, edge to edge, on the page background, with no surface color,
shadow or divider. Compose: `AppActionScaffold` in `:core:designsystem`; SwiftUI: `ActionScaffold`
in WhitelabelKit.

- **When:** full screens with a primary action (onboarding steps, forms, a hero "plant it" moment).
  Not tab roots, sheets, dialogs, centred empty or error states, settings pages or keypads.
- **Slots**, top to bottom in the dock: `message` (an error line), `primary` (full width, at least
  the pack's control height), at most one `secondary`, then `finePrint`. Never two buttons side by
  side. Plus `topBar`, a banner 8 above the dock (`snackbarHost` on Android, `banner` on iOS: put
  undo banners and one-off messages there, never in a bottom overlay of the screen, which would cover
  the primary), `contentAlignment` / `alignment` (`Top` for forms, `Center` for a hero that is
  centred between the top bar and the dock when it is shorter) and `scrollState` (Android).
- **The message is announced:** on Android it is a polite live region; on iOS pass its text as
  `messageAnnouncement` and VoiceOver announces each new line.
- **Spacing:** dock sides `sizing.screenGutter`, top 12, bottom 16 plus the bottom inset; items
  `spacing.sm` (8) apart; the content gets the same gutter and 24 (`spacing.lg`) below it.
- **Tokens** (`Sizing`, with defaults, so existing packs are unchanged):

  | Token | Default | What it does |
  |---|---|---|
  | `dockFadeHeight` | 24 dp / pt | a fade of the background above the dock, only while the content can scroll further down (150 ms; instant with reduced motion) |
  | `dockMaxFraction` | 0.4 | the most of the available height the dock may take |

- **The 40 % rule:** the primary never leaves the dock. When the dock would be taller than
  `dockMaxFraction` of the available height (large text, landscape, split screen), the fine print
  moves to the end of the content first, then the secondary, in the dock's order.
- **Keyboard:** the dock sits on the keyboard (Android: the IME inset is in the dock's bottom
  padding; iOS: the dock respects the keyboard's safe area). With the keyboard up, the rule weighs
  the dock against the height left above it, and with less than 360 left the fine print and the
  secondary move to the content too. iOS tells the keyboard from the home indicator by the bottom
  inset (over 100 pt), so an iPad hardware keyboard's shortcut bar counts as the home indicator.
- **Insets belong to the scaffold:** it pads the top and sides around everything and the bottom
  inside the dock, so the dock's background runs to the edge. Callers don't add their own
  `safeDrawingPadding()` / `windowInsetsPadding`. On Android, insets a parent already consumed (the
  shell pads for the system bars) are left out, both from the padding and from the 40 % and 360
  rules. The top bar sits below the status bar, so a top bar with its own surface color leaves a
  strip of page background above it when the screen isn't in the shell (the shell already pads the
  top).
- A disabled or loading primary keeps its place and size. The dock comes last in the reading order
  (TalkBack and VoiceOver).
- Test tags (Android): `AppActionScaffold`, `AppActionScaffoldContent` (the scroll),
  `AppActionDock`, `AppActionDockFade`. iOS identifiers: `ActionScaffold`, `ActionScaffold.content`,
  `ActionDock`, `ActionDock.fade`.

## Fonts (optional)

Put `font/brand_display.ttf` (or `.otf`) and `font/brand_body.ttf` in the app's `res/font/`:
- **Display font:** used for display, headline and title styles.
- **Body font:** used for body and label styles.

A missing font falls back to the system font. Use fonts whose license allows bundling (OFL fonts
from Google Fonts are fine), and keep the license file next to `app.properties`.

## Name and splash

The launcher label comes from `app.name` in `app.properties`, not from a string resource. The splash
uses `brand_splash_background` and `brand_splash_icon`.

## Previewing

In debug builds, Settings › About › Diagnostics shows the app's identity and the full design-system
catalog in the app's brand. To check dark mode, switch the emulator's theme with
`adb shell cmd uimode night yes`.
