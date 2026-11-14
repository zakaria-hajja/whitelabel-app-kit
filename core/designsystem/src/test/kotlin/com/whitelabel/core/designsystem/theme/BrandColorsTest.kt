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

    private fun roles(light: Boolean): List<Triple<String, (ColorScheme) -> Color, Int>> = if (light) {
        listOf(
            Triple("primary", { s: ColorScheme -> s.primary }, R.color.brand_light_primary),
            Triple("surfaceContainerLowest", { s: ColorScheme -> s.surfaceContainerLowest }, R.color.brand_light_surface_container_lowest),
            Triple("surfaceContainerLow", { s: ColorScheme -> s.surfaceContainerLow }, R.color.brand_light_surface_container_low),
            Triple("surfaceContainer", { s: ColorScheme -> s.surfaceContainer }, R.color.brand_light_surface_container),
            Triple("surfaceContainerHigh", { s: ColorScheme -> s.surfaceContainerHigh }, R.color.brand_light_surface_container_high),
            Triple("surfaceContainerHighest", { s: ColorScheme -> s.surfaceContainerHighest }, R.color.brand_light_surface_container_highest),
            Triple("outlineVariant", { s: ColorScheme -> s.outlineVariant }, R.color.brand_light_outline_variant),
            Triple("inverseSurface", { s: ColorScheme -> s.inverseSurface }, R.color.brand_light_inverse_surface),
            Triple("inverseOnSurface", { s: ColorScheme -> s.inverseOnSurface }, R.color.brand_light_inverse_on_surface),
            Triple("inversePrimary", { s: ColorScheme -> s.inversePrimary }, R.color.brand_light_inverse_primary),
        )
    } else {
        listOf(
            Triple("primary", { s: ColorScheme -> s.primary }, R.color.brand_dark_primary),
            Triple("surfaceContainerLowest", { s: ColorScheme -> s.surfaceContainerLowest }, R.color.brand_dark_surface_container_lowest),
            Triple("surfaceContainerLow", { s: ColorScheme -> s.surfaceContainerLow }, R.color.brand_dark_surface_container_low),
            Triple("surfaceContainer", { s: ColorScheme -> s.surfaceContainer }, R.color.brand_dark_surface_container),
            Triple("surfaceContainerHigh", { s: ColorScheme -> s.surfaceContainerHigh }, R.color.brand_dark_surface_container_high),
            Triple("surfaceContainerHighest", { s: ColorScheme -> s.surfaceContainerHighest }, R.color.brand_dark_surface_container_highest),
            Triple("outlineVariant", { s: ColorScheme -> s.outlineVariant }, R.color.brand_dark_outline_variant),
            Triple("inverseSurface", { s: ColorScheme -> s.inverseSurface }, R.color.brand_dark_inverse_surface),
            Triple("inverseOnSurface", { s: ColorScheme -> s.inverseOnSurface }, R.color.brand_dark_inverse_on_surface),
            Triple("inversePrimary", { s: ColorScheme -> s.inversePrimary }, R.color.brand_dark_inverse_primary),
        )
    }

    private fun mismatches(dark: Boolean): String {
        val scheme = brandColorScheme(darkTheme = dark, color = fake)
        return roles(light = !dark)
            .filter { (_, role, id) -> role(scheme) != fake(id) }
            .joinToString { it.first }
    }

    @Test fun `light scheme reads each role from its own resource`() {
        assertThat(mismatches(dark = false)).isEqualTo("")
    }

    @Test fun `dark scheme reads each role from its own resource`() {
        assertThat(mismatches(dark = true)).isEqualTo("")
    }
}
