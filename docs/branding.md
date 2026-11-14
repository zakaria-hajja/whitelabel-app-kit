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
  Material wherever it sets nothing. The surface-container roles stay lilac until you set them.
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
| Corners | `brand_corner_*` resources | 4 / 8 / 14 dp |
| Surfaces | elevation shadows | 1 dp hairlines (cards outlined, a rule above the bottom bar) |
| Controls | 48 dp | 52 dp |
| Type | Material weights; titles in the display font | display and headlines at regular weight; titles in the body font |
| Motion | Navigation's default 700 ms fades | 420 ms pushes and shared elements, 320 ms back, a 210 ms fade-through between tabs |
| Haptics | none | a tick on confirm, a confirm on success, a toggle click |
| Top bar (`AppScaffold`) | Material's raised bar on the surface color | flat on the page background, title in the headline (display) font |
| Section headers (`AppSectionHeader`) | `titleSmall` in the primary color | a small uppercase caption, letter-spaced, muted |
| Value rows (`SettingsValueRow`) | label and value on one line | the value under the label, muted |

- An app that never calls `style()` gets `Material`, which is exactly the look apps had before packs.
- **Bottom bar on the page surface:** `StylePack.Gallery.copy(bottomBarOnSurface = true)` draws
  the bar on `surface`, under its `outline_variant` hairline, instead of on `surface_container`.
  It is off by default and only applies to Hairline packs: a Shadow pack needs the container color
  to set the bar apart.
- Vary a pack with `copy`: `StylePack.Gallery.copy(sizing = StylePack.Gallery.sizing.copy(controlHeight = 56.dp))`.
- With Android's "Remove animations" on, every transition becomes a 150 ms crossfade, and
  `AppTheme.reducedMotion` is true so features can stop their own ambient animations.
- The diagnostics catalog shows the active pack's name.
- An app's own screens use `AppSectionHeader` for their section titles, so they match the shared
  screens under any pack.

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
