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

    @Test fun `the cap follows Android 14's non-linear scaling`() {
        // At 150 % Android maps 20 sp to 26 px, not 30, and 24 px back to 18 sp, not 16.
        val d = density(1.5f)
        val size = d.tabLabelMaxFontSize(20.sp, maxFontScale = 1.2f)
        assertThat(size.value).isCloseTo(18f, 0.001f)
        // 20 sp at 100 % x 1.2 = 24 px.
        assertThat(with(d) { size.toPx() }).isCloseTo(24f, 0.001f)
    }

    @Test fun `a capped label's floor is still its 100 % size, not the cap's`() {
        // 1.5 cap at 200 %: max 9 sp (18 px). The floor is 12 px = 6 sp, not 9 px = 4.5 sp.
        val d = density(2f)
        val floor = d.tabLabelFloor(fullFontSize = 12.sp, maxFontSize = 9.sp)
        assertThat(floor.value).isCloseTo(6f, 0.001f)
        assertThat(with(d) { floor.toPx() }).isCloseTo(12f, 0.001f)
    }

    @Test fun `an uncapped label's floor is its 100 % size`() {
        assertThat(density(2f).tabLabelFloor(fullFontSize = 12.sp, maxFontSize = 12.sp).value).isCloseTo(6f, 0.001f)
    }

    @Test fun `below 100 % the floor is the max, so the label never grows to fit`() {
        assertThat(density(0.85f).tabLabelFloor(fullFontSize = 12.sp, maxFontSize = 12.sp)).isEqualTo(12.sp)
    }
}
