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
surface_variant on_surface_variant outline`

- **Defaults:** any role you don't override keeps the Material 3 baseline value from
  `core/designsystem/src/main/res/values/brand.xml`.
- **Override more than primary:** components such as the bottom-bar indicator use
  `secondary_container`. An app that only overrides the primary family, as showcase and globex do
  today, shows the baseline lavender there. Override at least the primary, secondary and tertiary
  families for a fully branded look.
- **Contrast:** every `on_*` color must reach WCAG AA (4.5:1) against its base color, in both light
  and dark. Write the ratios in a comment in your `brand.xml`, as `apps/globex` does.
  `contrastRatio()` in `:core:designsystem` computes them.
- **Dynamic color is off,** so the brand always applies.

## Shapes

`dimen/brand_corner_small`, `brand_corner_medium` and `brand_corner_large`. The defaults are 8, 12
and 16dp; globex uses 0, 2 and 4dp for a square look.

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
