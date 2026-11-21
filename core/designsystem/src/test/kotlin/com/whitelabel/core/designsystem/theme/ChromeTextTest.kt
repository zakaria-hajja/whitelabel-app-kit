package com.whitelabel.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test

/**
 * The opt-in text styles for shared chrome (the Caption section header, the flat top bar's title).
 * Unset, each keeps today's Material role, so an app that doesn't opt in renders as before.
 */
class ChromeTextTest {

    // Neutral fixture fonts: the body and display families differ, so the test sees which one is used.
    private val typography = Typography().let { base ->
        Typography(
            displayLarge = base.displayLarge.copy(fontFamily = FontFamily.Serif),
            headlineSmall = base.headlineSmall.copy(fontFamily = FontFamily.Serif),
            bodyLarge = base.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            labelSmall = base.labelSmall.copy(fontFamily = FontFamily.Monospace),
        )
    }

    @Test fun `no pack sets chrome text, so Munch and every existing app keep theirs`() {
        for (pack in listOf(StylePack.Material, StylePack.Gallery)) {
            assertThat(pack.sectionCaptionText).isNull()
            assertThat(pack.flatTopBarTitleText).isNull()
        }
    }

    @Test fun `unset caption keeps labelSmall with 0_12 em tracking`() {
        assertThat(sectionCaptionStyle(null, typography))
            .isEqualTo(typography.labelSmall.copy(letterSpacing = 0.12.em))
    }

    @Test fun `a caption spec sets size, line height, weight and tracking in the body font`() {
        val style = sectionCaptionStyle(TextSpec(12.sp, 16.sp, FontWeight.SemiBold, letterSpacing = 0.6.sp), typography)
        assertThat(style.fontSize).isEqualTo(12.sp)
        assertThat(style.lineHeight).isEqualTo(16.sp)
        assertThat(style.fontWeight).isEqualTo(FontWeight.SemiBold)
        assertThat(style.letterSpacing).isEqualTo(0.6.sp)
        assertThat(style.fontFamily).isEqualTo(FontFamily.Monospace)
    }

    @Test fun `unset flat title keeps headlineSmall`() {
        assertThat(flatTopBarTitleStyle(null, typography)).isEqualTo(typography.headlineSmall)
    }

    @Test fun `a flat title spec sets size, line height and weight in the body font`() {
        val style = flatTopBarTitleStyle(TextSpec(18.sp, 26.sp, FontWeight.SemiBold), typography)
        assertThat(style.fontSize).isEqualTo(18.sp)
        assertThat(style.lineHeight).isEqualTo(26.sp)
        assertThat(style.fontWeight).isEqualTo(FontWeight.SemiBold)
        assertThat(style.letterSpacing).isEqualTo(0.sp)
        assertThat(style.fontFamily).isEqualTo(FontFamily.Monospace)
    }

    @Test fun `a spec can ask for the display font`() {
        val style = flatTopBarTitleStyle(TextSpec(18.sp, 26.sp, FontWeight.SemiBold, font = BrandFont.Display), typography)
        assertThat(style.fontFamily).isEqualTo(FontFamily.Serif)
    }
}
