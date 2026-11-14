package com.whitelabel.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import com.whitelabel.core.designsystem.R

/**
 * Builds the Material 3 color scheme from the `brand_light_*` / `brand_dark_*` color resources.
 * A client re-skins the app by overriding those names in its own `res/values/brand.xml`.
 */
@Composable
fun brandColorScheme(darkTheme: Boolean): ColorScheme {
    // Read like colorResource does, so a configuration change (night mode, locale) recomposes.
    val resources = LocalResources.current
    val theme = LocalContext.current.theme
    return brandColorScheme(darkTheme) { id -> Color(resources.getColor(id, theme)) }
}

/** The color scheme with each `R.color` id resolved through [color]; pure, so a JVM test can check the wiring. */
internal fun brandColorScheme(darkTheme: Boolean, color: (Int) -> Color): ColorScheme =
    if (darkTheme) {
        darkColorScheme(
            primary = color(R.color.brand_dark_primary),
            onPrimary = color(R.color.brand_dark_on_primary),
            primaryContainer = color(R.color.brand_dark_primary_container),
            onPrimaryContainer = color(R.color.brand_dark_on_primary_container),
            secondary = color(R.color.brand_dark_secondary),
            onSecondary = color(R.color.brand_dark_on_secondary),
            secondaryContainer = color(R.color.brand_dark_secondary_container),
            onSecondaryContainer = color(R.color.brand_dark_on_secondary_container),
            tertiary = color(R.color.brand_dark_tertiary),
            onTertiary = color(R.color.brand_dark_on_tertiary),
            tertiaryContainer = color(R.color.brand_dark_tertiary_container),
            onTertiaryContainer = color(R.color.brand_dark_on_tertiary_container),
            error = color(R.color.brand_dark_error),
            onError = color(R.color.brand_dark_on_error),
            background = color(R.color.brand_dark_background),
            onBackground = color(R.color.brand_dark_on_background),
            surface = color(R.color.brand_dark_surface),
            onSurface = color(R.color.brand_dark_on_surface),
            surfaceVariant = color(R.color.brand_dark_surface_variant),
            onSurfaceVariant = color(R.color.brand_dark_on_surface_variant),
            outline = color(R.color.brand_dark_outline),
            outlineVariant = color(R.color.brand_dark_outline_variant),
            surfaceContainerLowest = color(R.color.brand_dark_surface_container_lowest),
            surfaceContainerLow = color(R.color.brand_dark_surface_container_low),
            surfaceContainer = color(R.color.brand_dark_surface_container),
            surfaceContainerHigh = color(R.color.brand_dark_surface_container_high),
            surfaceContainerHighest = color(R.color.brand_dark_surface_container_highest),
            inverseSurface = color(R.color.brand_dark_inverse_surface),
            inverseOnSurface = color(R.color.brand_dark_inverse_on_surface),
            inversePrimary = color(R.color.brand_dark_inverse_primary),
            errorContainer = color(R.color.brand_dark_error_container),
            onErrorContainer = color(R.color.brand_dark_on_error_container),
            surfaceBright = color(R.color.brand_dark_surface_bright),
            surfaceDim = color(R.color.brand_dark_surface_dim),
            primaryFixed = color(R.color.brand_dark_primary_fixed),
            primaryFixedDim = color(R.color.brand_dark_primary_fixed_dim),
            onPrimaryFixed = color(R.color.brand_dark_on_primary_fixed),
            onPrimaryFixedVariant = color(R.color.brand_dark_on_primary_fixed_variant),
            secondaryFixed = color(R.color.brand_dark_secondary_fixed),
            secondaryFixedDim = color(R.color.brand_dark_secondary_fixed_dim),
            onSecondaryFixed = color(R.color.brand_dark_on_secondary_fixed),
            onSecondaryFixedVariant = color(R.color.brand_dark_on_secondary_fixed_variant),
            tertiaryFixed = color(R.color.brand_dark_tertiary_fixed),
            tertiaryFixedDim = color(R.color.brand_dark_tertiary_fixed_dim),
            onTertiaryFixed = color(R.color.brand_dark_on_tertiary_fixed),
            onTertiaryFixedVariant = color(R.color.brand_dark_on_tertiary_fixed_variant),
        )
    } else {
        lightColorScheme(
            primary = color(R.color.brand_light_primary),
            onPrimary = color(R.color.brand_light_on_primary),
            primaryContainer = color(R.color.brand_light_primary_container),
            onPrimaryContainer = color(R.color.brand_light_on_primary_container),
            secondary = color(R.color.brand_light_secondary),
            onSecondary = color(R.color.brand_light_on_secondary),
            secondaryContainer = color(R.color.brand_light_secondary_container),
            onSecondaryContainer = color(R.color.brand_light_on_secondary_container),
            tertiary = color(R.color.brand_light_tertiary),
            onTertiary = color(R.color.brand_light_on_tertiary),
            tertiaryContainer = color(R.color.brand_light_tertiary_container),
            onTertiaryContainer = color(R.color.brand_light_on_tertiary_container),
            error = color(R.color.brand_light_error),
            onError = color(R.color.brand_light_on_error),
            background = color(R.color.brand_light_background),
            onBackground = color(R.color.brand_light_on_background),
            surface = color(R.color.brand_light_surface),
            onSurface = color(R.color.brand_light_on_surface),
            surfaceVariant = color(R.color.brand_light_surface_variant),
            onSurfaceVariant = color(R.color.brand_light_on_surface_variant),
            outline = color(R.color.brand_light_outline),
            outlineVariant = color(R.color.brand_light_outline_variant),
            surfaceContainerLowest = color(R.color.brand_light_surface_container_lowest),
            surfaceContainerLow = color(R.color.brand_light_surface_container_low),
            surfaceContainer = color(R.color.brand_light_surface_container),
            surfaceContainerHigh = color(R.color.brand_light_surface_container_high),
            surfaceContainerHighest = color(R.color.brand_light_surface_container_highest),
            inverseSurface = color(R.color.brand_light_inverse_surface),
            inverseOnSurface = color(R.color.brand_light_inverse_on_surface),
            inversePrimary = color(R.color.brand_light_inverse_primary),
            errorContainer = color(R.color.brand_light_error_container),
            onErrorContainer = color(R.color.brand_light_on_error_container),
            surfaceBright = color(R.color.brand_light_surface_bright),
            surfaceDim = color(R.color.brand_light_surface_dim),
            primaryFixed = color(R.color.brand_light_primary_fixed),
            primaryFixedDim = color(R.color.brand_light_primary_fixed_dim),
            onPrimaryFixed = color(R.color.brand_light_on_primary_fixed),
            onPrimaryFixedVariant = color(R.color.brand_light_on_primary_fixed_variant),
            secondaryFixed = color(R.color.brand_light_secondary_fixed),
            secondaryFixedDim = color(R.color.brand_light_secondary_fixed_dim),
            onSecondaryFixed = color(R.color.brand_light_on_secondary_fixed),
            onSecondaryFixedVariant = color(R.color.brand_light_on_secondary_fixed_variant),
            tertiaryFixed = color(R.color.brand_light_tertiary_fixed),
            tertiaryFixedDim = color(R.color.brand_light_tertiary_fixed_dim),
            onTertiaryFixed = color(R.color.brand_light_on_tertiary_fixed),
            onTertiaryFixedVariant = color(R.color.brand_light_on_tertiary_fixed_variant),
        )
    }
