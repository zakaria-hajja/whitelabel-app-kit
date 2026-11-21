package com.whitelabel.core.shell

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

/**
 * [tabLabelMaxFontSize]: the size a bottom-bar label starts from. With no cap it is the style's
 * size (the user's full size); with a cap it renders no bigger than the 100 % size times the cap.
 */
class TabLabelCapTest {

    /** Linear font scaling: 1 sp = fontScale px at density 1. */
    private fun density(fontScale: Float) = object : Density {
        override val density = 1f
        override val fontScale = fontScale
    }

    @Test fun `no cap keeps the full size at 200 %`() {
        assertThat(density(2f).tabLabelMaxFontSize(12.sp, maxFontScale = null)).isEqualTo(12.sp)
    }

    @Test fun `a 1_5 cap at 200 % renders at 150 % of the default size`() {
        val d = density(2f)
        val size = d.tabLabelMaxFontSize(12.sp, maxFontScale = 1.5f)
        assertThat(size.value).isCloseTo(9f, 0.001f)
        // 9 sp at 200 % = 18 px = 12 sp at 100 % x 1.5.
        assertThat(with(d) { size.toPx() }).isCloseTo(18f, 0.001f)
    }

    @Test fun `a cap above the user's scale changes nothing`() {
        assertThat(density(1.3f).tabLabelMaxFontSize(12.sp, maxFontScale = 1.5f)).isEqualTo(12.sp)
    }

    @Test fun `at exactly the cap the size is unchanged`() {
        assertThat(density(1.5f).tabLabelMaxFontSize(12.sp, maxFontScale = 1.5f).value).isCloseTo(12f, 0.001f)
    }

    @Test fun `below 100 % a cap changes nothing`() {
        assertThat(density(0.85f).tabLabelMaxFontSize(12.sp, maxFontScale = 1.5f)).isEqualTo(12.sp)
    }
}
