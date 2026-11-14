package com.whitelabel.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.whitelabel.core.designsystem.R
import org.junit.jupiter.api.Test

/**
 * Each role must read its own resource. The default values equal Material's baseline, so a role
 * that was never wired would still look right on device: only this test tells them apart.
 */
class BrandColorsTest {

    /** A distinct, opaque color per resource id. */
    private val fake: (Int) -> Color = { id -> Color(id or 0xFF000000.toInt()) }

    private class Role(val name: String, val read: (ColorScheme) -> Color, val light: Int, val dark: Int)

    /** All 30 roles `brandColorScheme` sets, each with its light and dark resource id. */
    private val roles = listOf(
        Role("primary", { it.primary }, R.color.brand_light_primary, R.color.brand_dark_primary),
        Role("onPrimary", { it.onPrimary }, R.color.brand_light_on_primary, R.color.brand_dark_on_primary),
        Role("primaryContainer", { it.primaryContainer }, R.color.brand_light_primary_container, R.color.brand_dark_primary_container),
        Role("onPrimaryContainer", { it.onPrimaryContainer }, R.color.brand_light_on_primary_container, R.color.brand_dark_on_primary_container),
        Role("secondary", { it.secondary }, R.color.brand_light_secondary, R.color.brand_dark_secondary),
        Role("onSecondary", { it.onSecondary }, R.color.brand_light_on_secondary, R.color.brand_dark_on_secondary),
        Role("secondaryContainer", { it.secondaryContainer }, R.color.brand_light_secondary_container, R.color.brand_dark_secondary_container),
        Role("onSecondaryContainer", { it.onSecondaryContainer }, R.color.brand_light_on_secondary_container, R.color.brand_dark_on_secondary_container),
        Role("tertiary", { it.tertiary }, R.color.brand_light_tertiary, R.color.brand_dark_tertiary),
        Role("onTertiary", { it.onTertiary }, R.color.brand_light_on_tertiary, R.color.brand_dark_on_tertiary),
        Role("tertiaryContainer", { it.tertiaryContainer }, R.color.brand_light_tertiary_container, R.color.brand_dark_tertiary_container),
        Role("onTertiaryContainer", { it.onTertiaryContainer }, R.color.brand_light_on_tertiary_container, R.color.brand_dark_on_tertiary_container),
        Role("error", { it.error }, R.color.brand_light_error, R.color.brand_dark_error),
        Role("onError", { it.onError }, R.color.brand_light_on_error, R.color.brand_dark_on_error),
        Role("background", { it.background }, R.color.brand_light_background, R.color.brand_dark_background),
        Role("onBackground", { it.onBackground }, R.color.brand_light_on_background, R.color.brand_dark_on_background),
        Role("surface", { it.surface }, R.color.brand_light_surface, R.color.brand_dark_surface),
        Role("onSurface", { it.onSurface }, R.color.brand_light_on_surface, R.color.brand_dark_on_surface),
        Role("surfaceVariant", { it.surfaceVariant }, R.color.brand_light_surface_variant, R.color.brand_dark_surface_variant),
        Role("onSurfaceVariant", { it.onSurfaceVariant }, R.color.brand_light_on_surface_variant, R.color.brand_dark_on_surface_variant),
        Role("outline", { it.outline }, R.color.brand_light_outline, R.color.brand_dark_outline),
        Role("outlineVariant", { it.outlineVariant }, R.color.brand_light_outline_variant, R.color.brand_dark_outline_variant),
        Role("surfaceContainerLowest", { it.surfaceContainerLowest }, R.color.brand_light_surface_container_lowest, R.color.brand_dark_surface_container_lowest),
        Role("surfaceContainerLow", { it.surfaceContainerLow }, R.color.brand_light_surface_container_low, R.color.brand_dark_surface_container_low),
        Role("surfaceContainer", { it.surfaceContainer }, R.color.brand_light_surface_container, R.color.brand_dark_surface_container),
        Role("surfaceContainerHigh", { it.surfaceContainerHigh }, R.color.brand_light_surface_container_high, R.color.brand_dark_surface_container_high),
        Role("surfaceContainerHighest", { it.surfaceContainerHighest }, R.color.brand_light_surface_container_highest, R.color.brand_dark_surface_container_highest),
        Role("inverseSurface", { it.inverseSurface }, R.color.brand_light_inverse_surface, R.color.brand_dark_inverse_surface),
        Role("inverseOnSurface", { it.inverseOnSurface }, R.color.brand_light_inverse_on_surface, R.color.brand_dark_inverse_on_surface),
        Role("inversePrimary", { it.inversePrimary }, R.color.brand_light_inverse_primary, R.color.brand_dark_inverse_primary),
    )

    private fun mismatches(dark: Boolean): String {
        val scheme = brandColorScheme(darkTheme = dark, color = fake)
        return roles
            .filter { it.read(scheme) != fake(if (dark) it.dark else it.light) }
            .joinToString { it.name }
    }

    @Test fun `light scheme reads each role from its own resource`() {
        assertThat(mismatches(dark = false)).isEqualTo("")
    }

    @Test fun `the table covers every role`() {
        assertThat(roles.map { it.name }.distinct().size).isEqualTo(30)
    }

    @Test fun `dark scheme reads each role from its own resource`() {
        assertThat(mismatches(dark = true)).isEqualTo("")
    }
}
