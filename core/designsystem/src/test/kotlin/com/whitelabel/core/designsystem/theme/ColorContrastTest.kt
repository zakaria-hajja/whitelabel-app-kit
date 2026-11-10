package com.whitelabel.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isLessThan
import org.junit.jupiter.api.Test

class ColorContrastTest {
    @Test
    fun blackOnWhite_is21() {
        assertThat(contrastRatio(Color.Black, Color.White)).isBetween(20.99, 21.01)
    }

    @Test
    fun identicalColors_is1() {
        assertThat(contrastRatio(Color.Red, Color.Red)).isBetween(0.999, 1.001)
    }

    @Test
    fun ratio_isSymmetric() {
        val a = Color(0xFF3366CC)
        val b = Color(0xFFFFEEAA)
        assertThat(contrastRatio(a, b)).isEqualTo(contrastRatio(b, a))
    }

    @Test
    fun aaThreshold_boundaryGreyOnWhite() {
        assertThat(contrastRatio(Color(0xFF767676), Color.White)).isGreaterThanOrEqualTo(4.5)
        assertThat(contrastRatio(Color(0xFF777777), Color.White)).isLessThan(4.5)
    }
}
