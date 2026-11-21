package com.whitelabel.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class BrandTypographyTest {

    private val material = Typography()
    private val display = FontFamily.Serif
    private val body = FontFamily.SansSerif

    @Test fun `label weight unset keeps Material's labelLarge weight`() {
        val type = typographyOf(display, body, StylePack.Material.type)
        assertThat(type.labelLarge.fontWeight).isEqualTo(material.labelLarge.fontWeight)
        assertThat(type.labelLarge).isEqualTo(material.labelLarge.copy(fontFamily = body))
    }

    @Test fun `label weight sets labelLarge, the role Material buttons use`() {
        val type = typographyOf(display, body, StylePack.Material.type.copy(labelWeight = FontWeight.SemiBold))
        assertThat(type.labelLarge.fontWeight).isEqualTo(FontWeight.SemiBold)
        assertThat(type.labelLarge.fontFamily).isEqualTo(body)
        assertThat(type.labelLarge.fontSize).isEqualTo(material.labelLarge.fontSize)
        assertThat(type.labelLarge.letterSpacing).isEqualTo(material.labelLarge.letterSpacing)
    }

    @Test fun `label weight leaves the smaller labels alone`() {
        val type = typographyOf(display, body, StylePack.Material.type.copy(labelWeight = FontWeight.SemiBold))
        assertThat(type.labelMedium.fontWeight).isEqualTo(material.labelMedium.fontWeight)
        assertThat(type.labelSmall.fontWeight).isEqualTo(material.labelSmall.fontWeight)
    }

    @Test fun `heading weights and the title font still apply`() {
        val type = typographyOf(display, body, StylePack.Gallery.type)
        assertThat(type.displayLarge.fontWeight).isEqualTo(FontWeight.Normal)
        assertThat(type.headlineSmall.fontWeight).isEqualTo(FontWeight.Normal)
        assertThat(type.headlineSmall.fontFamily).isEqualTo(display)
        assertThat(type.titleLarge.fontFamily).isEqualTo(body)
        assertThat(type.bodyLarge.fontFamily).isEqualTo(body)
    }

    @Test fun `no pack sets a label weight today`() {
        assertThat(StylePack.Material.type.labelWeight).isEqualTo(null)
        assertThat(StylePack.Gallery.type.labelWeight).isEqualTo(null)
    }
}
