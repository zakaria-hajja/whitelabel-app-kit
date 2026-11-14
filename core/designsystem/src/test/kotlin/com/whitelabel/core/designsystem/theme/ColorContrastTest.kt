package com.whitelabel.core.designsystem.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import assertk.assertThat
import assertk.assertions.containsAll
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

    @Test
    fun contrastPairs_coverBarDialogAndSnackbarText() {
        val s = lightColorScheme()
        assertThat(s.contrastPairs()).containsAll(
            ContrastPair("onSurfaceVariant/surfaceContainer", s.onSurfaceVariant, s.surfaceContainer),
            ContrastPair("onSurface/surfaceContainerHigh", s.onSurface, s.surfaceContainerHigh),
            ContrastPair("inverseOnSurface/inverseSurface", s.inverseOnSurface, s.inverseSurface),
        )
    }

    @Test
    fun contrastPairs_coverErrorContainerAndFixedText() {
        val s = lightColorScheme()
        assertThat(s.contrastPairs()).containsAll(
            ContrastPair("onErrorContainer/errorContainer", s.onErrorContainer, s.errorContainer),
            ContrastPair("onPrimaryFixed/primaryFixed", s.onPrimaryFixed, s.primaryFixed),
            ContrastPair("onPrimaryFixedVariant/primaryFixedDim", s.onPrimaryFixedVariant, s.primaryFixedDim),
            ContrastPair("onSecondaryFixed/secondaryFixed", s.onSecondaryFixed, s.secondaryFixed),
            ContrastPair("onSecondaryFixedVariant/secondaryFixedDim", s.onSecondaryFixedVariant, s.secondaryFixedDim),
            ContrastPair("onTertiaryFixed/tertiaryFixed", s.onTertiaryFixed, s.tertiaryFixed),
            ContrastPair("onTertiaryFixedVariant/tertiaryFixedDim", s.onTertiaryFixedVariant, s.tertiaryFixedDim),
        )
    }
}
