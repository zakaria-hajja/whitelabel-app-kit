package com.whitelabel.core.designsystem.component

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test

class ActionDockLayoutTest {

    // Top 12 + bottom 16 padding + a 48 bottom inset.
    private val chrome = 28 + 48
    private val gap = 8

    private fun overflow(
        available: Int,
        primary: Int,
        secondary: Int = 0,
        finePrint: Int = 0,
        message: Int = 0,
        compact: Boolean = false,
    ) = dockOverflow(
        available = available, chrome = chrome, gap = gap,
        message = message, primary = primary, secondary = secondary, finePrint = finePrint,
        maxFraction = 0.4f, compact = compact,
    )

    @Test fun `a dock that fits keeps everything`() {
        assertThat(overflow(available = 2000, primary = 52, secondary = 48, finePrint = 40)).isEqualTo(DockOverflow.None)
    }

    @Test fun `tall fine print moves first`() {
        // Cap 320; full 76+104+8+96+8+160 = 452; without fine print 284.
        assertThat(overflow(available = 800, primary = 104, secondary = 96, finePrint = 160)).isEqualTo(DockOverflow.FinePrint)
    }

    @Test fun `a dock still too tall moves the secondary too`() {
        // Cap 200; without fine print 76+104+8+96 = 284.
        assertThat(overflow(available = 500, primary = 104, secondary = 96, finePrint = 60))
            .isEqualTo(DockOverflow.FinePrintAndSecondary)
    }

    @Test fun `the primary alone never overflows`() {
        assertThat(overflow(available = 100, primary = 200)).isEqualTo(DockOverflow.None)
    }

    @Test fun `absent parts add no gap`() {
        // Cap 400; 76 + message 16 + 8 + 300 = 400; a gap counted for each absent part would overflow.
        assertThat(overflow(available = 1000, primary = 300, message = 16)).isEqualTo(DockOverflow.None)
        // Exactly at the cap with a fine print only: 76 + 280 + 8 + 36 = 400.
        assertThat(overflow(available = 1000, primary = 280, finePrint = 36)).isEqualTo(DockOverflow.None)
        assertThat(overflow(available = 1000, primary = 281, finePrint = 36)).isEqualTo(DockOverflow.FinePrint)
    }

    @Test fun `the message stays in the dock and counts towards its height`() {
        // Cap 400; 76 + 40 + 8 + 200 + 8 + 80 = 412 → fine print moves; 324 fits.
        assertThat(overflow(available = 1000, message = 40, primary = 200, finePrint = 80)).isEqualTo(DockOverflow.FinePrint)
    }

    @Test fun `a secondary alone moves when it doesn't fit`() {
        assertThat(overflow(available = 500, primary = 104, secondary = 96)).isEqualTo(DockOverflow.FinePrintAndSecondary)
    }

    @Test fun `a compact keyboard window moves the extras`() {
        assertThat(overflow(available = 2000, primary = 52, secondary = 48, finePrint = 40, compact = true))
            .isEqualTo(DockOverflow.FinePrintAndSecondary)
        assertThat(overflow(available = 2000, primary = 52, compact = true)).isEqualTo(DockOverflow.None)
    }

    @Test fun `the fade shows only while the content can scroll further`() {
        assertThat(showsDockFade(canScrollForward = true)).isTrue()
        assertThat(showsDockFade(canScrollForward = false)).isFalse()
    }
}
