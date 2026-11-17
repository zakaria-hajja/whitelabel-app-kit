package com.whitelabel.core.ads.google

import androidx.compose.ui.unit.dp
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
}
