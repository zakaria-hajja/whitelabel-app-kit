package com.whitelabel.core.ads

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import org.junit.jupiter.api.Test

class AdBannerLogicTest {

    // Which unit, if any, the banner loads.

    @Test fun `a ready placement loads its unit`() {
        assertThat(bannerUnit(BannerState.Ready("u"), "home", debugBuild = true)).isEqualTo("u")
    }

    @Test fun `a hidden placement loads nothing`() {
        HideReason.entries.filter { it != HideReason.UNDECLARED }.forEach { reason ->
            assertThat(bannerUnit(BannerState.Hidden(reason), "home", debugBuild = true)).isNull()
        }
    }

    @Test fun `an undeclared placement fails loudly in debug`() {
        assertFailure { bannerUnit(BannerState.Hidden(HideReason.UNDECLARED), "x", debugBuild = true) }
            .isInstanceOf<IllegalStateException>()
            .hasMessage("placement 'x' isn't declared in ads { }")
    }

    @Test fun `an undeclared placement stays hidden in release`() {
        assertThat(bannerUnit(BannerState.Hidden(HideReason.UNDECLARED), "x", debugBuild = false)).isNull()
    }

    // The load state machine.

    @Test fun `loading becomes loaded or failed`() {
        assertThat(BannerLoad.LOADING.on(BannerEvent.LOADED)).isEqualTo(BannerLoad.LOADED)
        assertThat(BannerLoad.LOADING.on(BannerEvent.FAILED)).isEqualTo(BannerLoad.FAILED)
    }

    @Test fun `a failed refresh after a load keeps the last ad`() {
        assertThat(BannerLoad.LOADED.on(BannerEvent.FAILED)).isEqualTo(BannerLoad.LOADED)
        assertThat(BannerLoad.LOADED.on(BannerEvent.LOADED)).isEqualTo(BannerLoad.LOADED)
    }

    @Test fun `a late load after a failure shows the ad`() {
        assertThat(BannerLoad.FAILED.on(BannerEvent.LOADED)).isEqualTo(BannerLoad.LOADED)
        assertThat(BannerLoad.FAILED.on(BannerEvent.FAILED)).isEqualTo(BannerLoad.FAILED)
    }

    @Test fun `a new request after a resize or a new unit collapses until it loads`() {
        BannerLoad.entries.forEach { from ->
            assertThat(from.on(BannerEvent.REQUESTED)).isEqualTo(BannerLoad.LOADING)
        }
        // The new view's failure then stays collapsed instead of keeping a blank labelled gap.
        assertThat(BannerLoad.LOADED.on(BannerEvent.REQUESTED).on(BannerEvent.FAILED)).isEqualTo(BannerLoad.FAILED)
    }
}
