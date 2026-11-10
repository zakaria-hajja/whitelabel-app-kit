package com.whitelabel.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import com.whitelabel.core.designsystem.R

/**
 * Builds the Material 3 color scheme from the `brand_light_*` / `brand_dark_*` color resources.
 * A client re-skins the app by overriding those names in its own `res/values/brand.xml`.
 */
@Composable
fun brandColorScheme(darkTheme: Boolean): ColorScheme =
    if (darkTheme) {
        darkColorScheme(
            primary = colorResource(R.color.brand_dark_primary),
            onPrimary = colorResource(R.color.brand_dark_on_primary),
            primaryContainer = colorResource(R.color.brand_dark_primary_container),
            onPrimaryContainer = colorResource(R.color.brand_dark_on_primary_container),
            secondary = colorResource(R.color.brand_dark_secondary),
            onSecondary = colorResource(R.color.brand_dark_on_secondary),
            secondaryContainer = colorResource(R.color.brand_dark_secondary_container),
            onSecondaryContainer = colorResource(R.color.brand_dark_on_secondary_container),
            tertiary = colorResource(R.color.brand_dark_tertiary),
            onTertiary = colorResource(R.color.brand_dark_on_tertiary),
            tertiaryContainer = colorResource(R.color.brand_dark_tertiary_container),
            onTertiaryContainer = colorResource(R.color.brand_dark_on_tertiary_container),
            error = colorResource(R.color.brand_dark_error),
            onError = colorResource(R.color.brand_dark_on_error),
            background = colorResource(R.color.brand_dark_background),
            onBackground = colorResource(R.color.brand_dark_on_background),
            surface = colorResource(R.color.brand_dark_surface),
            onSurface = colorResource(R.color.brand_dark_on_surface),
            surfaceVariant = colorResource(R.color.brand_dark_surface_variant),
            onSurfaceVariant = colorResource(R.color.brand_dark_on_surface_variant),
            outline = colorResource(R.color.brand_dark_outline),
        )
    } else {
        lightColorScheme(
            primary = colorResource(R.color.brand_light_primary),
            onPrimary = colorResource(R.color.brand_light_on_primary),
            primaryContainer = colorResource(R.color.brand_light_primary_container),
            onPrimaryContainer = colorResource(R.color.brand_light_on_primary_container),
            secondary = colorResource(R.color.brand_light_secondary),
            onSecondary = colorResource(R.color.brand_light_on_secondary),
            secondaryContainer = colorResource(R.color.brand_light_secondary_container),
            onSecondaryContainer = colorResource(R.color.brand_light_on_secondary_container),
            tertiary = colorResource(R.color.brand_light_tertiary),
            onTertiary = colorResource(R.color.brand_light_on_tertiary),
            tertiaryContainer = colorResource(R.color.brand_light_tertiary_container),
            onTertiaryContainer = colorResource(R.color.brand_light_on_tertiary_container),
            error = colorResource(R.color.brand_light_error),
            onError = colorResource(R.color.brand_light_on_error),
            background = colorResource(R.color.brand_light_background),
            onBackground = colorResource(R.color.brand_light_on_background),
            surface = colorResource(R.color.brand_light_surface),
            onSurface = colorResource(R.color.brand_light_on_surface),
            surfaceVariant = colorResource(R.color.brand_light_surface_variant),
            onSurfaceVariant = colorResource(R.color.brand_light_on_surface_variant),
            outline = colorResource(R.color.brand_light_outline),
        )
    }
