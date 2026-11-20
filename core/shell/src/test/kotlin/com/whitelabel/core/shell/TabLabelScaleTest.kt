package com.whitelabel.core.shell

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

/**
 * Bottom-bar labels follow the font scale up to 150 %, then stop growing, so four one-word labels
 * still fit their tabs at 200 % instead of breaking mid-word ("Calenda/r").
 */
class TabLabelScaleTest {

    @Test fun `up to 150 percent the label scales as the system says`() {
        assertThat(tabLabelSizeFactor(fontScale = 1f)).isEqualTo(1f)
        assertThat(tabLabelSizeFactor(fontScale = 1.3f)).isEqualTo(1f)
        assertThat(tabLabelSizeFactor(fontScale = 1.5f)).isEqualTo(1f)
    }

    @Test fun `above 150 percent the label stays at 150 percent`() {
        assertThat(tabLabelSizeFactor(fontScale = 2f) * 2f).isEqualTo(1.5f)
        assertThat(tabLabelSizeFactor(fontScale = 3f) * 3f).isEqualTo(1.5f)
    }

    @Test fun `a smaller than default scale is kept`() {
        assertThat(tabLabelSizeFactor(fontScale = 0.85f)).isEqualTo(1f)
    }
}
