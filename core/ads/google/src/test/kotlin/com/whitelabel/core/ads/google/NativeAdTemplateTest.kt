package com.whitelabel.core.ads.google

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test

class NativeAdTemplateTest {
    @Test fun `policy minimums`() {
        assertThat(NativeAdTemplate.MediaSize).isGreaterThanOrEqualTo(120.dp)
        assertThat(NativeAdTemplate.BadgeMinSize).isGreaterThanOrEqualTo(15.dp)
        assertThat(NativeAdTemplate.Height).isGreaterThanOrEqualTo(NativeAdTemplate.MediaSize + NativeAdTemplate.AdChoicesInset)
    }

    // The same literals are pinned by iOS's NativeAdTemplateTests.
    @Test fun `the height at font scale 1 is the designed 184`() {
        assertThat(NativeAdTemplate.Height).isEqualTo(184.dp)
        assertThat(NativeAdTemplate.height(1f)).isEqualTo(184.dp)
    }

    @Test fun `smaller font scales keep the designed height`() {
        assertThat(NativeAdTemplate.height(0.85f)).isEqualTo(184.dp)
    }

    @Test fun `larger font scales grow the height by the spec's formula`() {
        assertThat(NativeAdTemplate.height(1.15f)).isEqualTo(198.dp)
        assertThat(NativeAdTemplate.height(1.3f)).isEqualTo(212.dp)
        assertThat(NativeAdTemplate.height(1.5f)).isEqualTo(230.dp)
        assertThat(NativeAdTemplate.height(1.8f)).isEqualTo(263.dp)
        assertThat(NativeAdTemplate.height(2f)).isEqualTo(284.dp)
    }

    @Test fun `an ad without a headline or media is not shown`() {
        assertThat(isRenderable(headline = "Quillby", hasMedia = true)).isTrue()
        assertThat(isRenderable(headline = null, hasMedia = true)).isFalse()
        assertThat(isRenderable(headline = "", hasMedia = true)).isFalse()
        assertThat(isRenderable(headline = "Quillby", hasMedia = false)).isFalse()
    }

    @Test fun `the spec's line heights give the same height as the font scale`() {
        for (scale in listOf(0.85f, 1f, 1.3f, 2f)) {
            assertThat(NativeAdTemplate.height(NativeAdTemplate.Lines.spec(scale)), "$scale").isEqualTo(NativeAdTemplate.height(scale))
        }
    }

    // An app whose type roles are taller than the spec's must not squeeze the CTA row out of the card.
    @Test fun `a taller headline line grows the card`() {
        val lines = NativeAdTemplate.Lines(label = 16f, headline = 24f, body = 16f, cta = 20f)
        // 16 + 24 + 8 + (2×24 + 4 + 2×16 + 4 + 40) + 16
        assertThat(NativeAdTemplate.height(lines)).isEqualTo(192.dp)
    }

    @Test fun `a taller CTA line grows the bottom row`() {
        val lines = NativeAdTemplate.Lines(label = 16f, headline = 20f, body = 16f, cta = 28f)
        // 16 + 24 + 8 + (2×20 + 4 + 2×16 + 4 + (28 + 20)) + 16
        assertThat(NativeAdTemplate.height(lines)).isEqualTo(192.dp)
    }

    @Test fun `a taller label or advertiser line grows the header`() {
        val lines = NativeAdTemplate.Lines(label = 30f, headline = 20f, body = 16f, cta = 20f)
        assertThat(NativeAdTemplate.height(lines)).isEqualTo(190.dp)
    }

    @Test fun `line heights come from the app's text styles`() {
        val lines = NativeAdTemplate.Lines.of(
            Density(density = 2f, fontScale = 1f),
            label = TextStyle(fontSize = 11.sp, lineHeight = 16.sp),
            headline = TextStyle(fontSize = 14.sp, lineHeight = 24.sp),
            body = TextStyle(fontSize = 10.sp, lineHeight = 2.em),
            cta = TextStyle(),
        )
        // em is relative to the font size; a style without a line height falls back to the spec's 20.
        assertThat(lines).isEqualTo(NativeAdTemplate.Lines(label = 16f, headline = 24f, body = 20f, cta = 20f))
    }

    // Android scales large text nonlinearly; the card follows what Text really draws.
    @Test fun `line heights follow the platform's font scaling`() {
        val density = Density(density = 2f, fontScale = 1.5f)
        val style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp)
        val lines = NativeAdTemplate.Lines.of(density, style, style, style, style)
        assertThat(lines.headline).isEqualTo(with(density) { 20.sp.toDp().value })
    }
}
