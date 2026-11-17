package com.whitelabel.core.ads

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import kotlin.test.Test

class NativeAdRotationTest {

    private var now = 0L
    private val rotation = NativeAdRotation(nowMillis = { now })

    private class FakeAd(val name: String) : NativeAdHandle {
        var destroyed = 0
        override fun destroy() {
            destroyed++
        }
        override fun toString() = name
    }

    private val a = FakeAd("a")
    private val b = FakeAd("b")
    private val c = FakeAd("c")

    /** A visit that requests, loads [ad] while shown, and ends. */
    private fun showAndLeave(ad: FakeAd) {
        rotation.beginVisit()
        rotation.loaded(ad)
        rotation.endVisit()
    }

    @Test fun `first visit requests and shows nothing`() {
        assertThat(rotation.beginVisit()).isEqualTo(NativeAdVisit(null, true))
    }

    @Test fun `loaded during the visit shows at once`() {
        rotation.beginVisit()
        assertThat(rotation.loaded(a)).isSameInstanceAs(a)
    }

    @Test fun `a visit within the refresh interval reuses the ad without a request`() {
        showAndLeave(a)
        now = 30_000
        assertThat(rotation.beginVisit()).isEqualTo(NativeAdVisit(a, false))
    }

    @Test fun `after the refresh interval the shown ad stays and a replacement is requested`() {
        showAndLeave(a)
        now = 61_000
        assertThat(rotation.beginVisit()).isEqualTo(NativeAdVisit(a, true))
        assertThat(rotation.loaded(b)).isNull()
        assertThat(a.destroyed).isEqualTo(0)
    }

    @Test fun `the replacement shows on the next visit and the old ad is destroyed`() {
        showAndLeave(a)
        now = 61_000
        rotation.beginVisit()
        rotation.loaded(b)
        rotation.endVisit()
        val visit = rotation.beginVisit()
        assertThat(visit).isEqualTo(NativeAdVisit(b, false))
        assertThat(a.destroyed).isEqualTo(1)
        assertThat(b.destroyed).isEqualTo(0)
    }

    @Test fun `loaded after the visit ended is kept for the next visit`() {
        rotation.beginVisit()
        rotation.endVisit()
        assertThat(rotation.loaded(a)).isNull()
        assertThat(rotation.beginVisit().ad).isSameInstanceAs(a)
        assertThat(a.destroyed).isEqualTo(0)
    }

    @Test fun `a replaced next is destroyed`() {
        showAndLeave(a)
        now = 61_000
        rotation.beginVisit()
        rotation.loaded(b)
        // A second load while b waits (the host delivered twice, or a late callback): b gives way to c.
        rotation.loaded(c)
        assertThat(b.destroyed).isEqualTo(1)
        rotation.endVisit()
        assertThat(rotation.beginVisit().ad).isSameInstanceAs(c)
        assertThat(a.destroyed).isEqualTo(1)
    }

    @Test fun `an ad older than an hour is destroyed and not shown`() {
        showAndLeave(a)
        now = 3_600_001
        assertThat(rotation.beginVisit()).isEqualTo(NativeAdVisit(null, true))
        assertThat(a.destroyed).isEqualTo(1)
    }

    @Test fun `an ad exactly an hour old still shows`() {
        showAndLeave(a)
        now = 3_600_000
        assertThat(rotation.beginVisit().ad).isSameInstanceAs(a)
    }

    @Test fun `a kept next older than an hour is destroyed and not promoted`() {
        rotation.beginVisit()
        rotation.endVisit()
        rotation.loaded(a)
        now = 3_600_001
        assertThat(rotation.beginVisit()).isEqualTo(NativeAdVisit(null, true))
        assertThat(a.destroyed).isEqualTo(1)
    }

    @Test fun `a failed load waits the refresh interval`() {
        rotation.beginVisit()
        rotation.failed()
        rotation.endVisit()
        now = 10_000
        assertThat(rotation.beginVisit().request).isFalse()
        rotation.endVisit()
        now = 60_000
        assertThat(rotation.beginVisit().request).isTrue()
    }

    @Test fun `no second request while one is in flight`() {
        rotation.beginVisit()
        rotation.endVisit()
        now = 120_000
        assertThat(rotation.beginVisit().request).isFalse()
    }

    @Test fun `a clock that went back before the load expires the ad`() {
        now = 100_000
        showAndLeave(a)
        now = 50_000
        // The age can't be known any more; an ad possibly over an hour old is never shown.
        assertThat(rotation.beginVisit().ad).isNull()
        assertThat(a.destroyed).isEqualTo(1)
    }

    @Test fun `a clock that went back before the last request doesn't block requests`() {
        now = 100_000
        rotation.beginVisit()
        rotation.failed()
        rotation.endVisit()
        now = 50_000
        assertThat(rotation.beginVisit().request).isTrue()
    }

    @Test fun `peek is empty before anything loaded`() {
        assertThat(rotation.peek()).isNull()
    }

    @Test fun `peek gives the held ad a visit would show, without beginning one`() {
        showAndLeave(a)
        now = 30_000
        assertThat(rotation.peek()).isSameInstanceAs(a)
        // Peeking neither began a visit nor used up the request: the visit still decides as before.
        assertThat(rotation.loaded(b)).isNull()
        assertThat(rotation.peek()).isSameInstanceAs(b)
        assertThat(a.destroyed).isEqualTo(0)
    }

    @Test fun `peek gives the next ad over the current one, as the next visit promotes it`() {
        showAndLeave(a)
        now = 60_000
        rotation.beginVisit()
        rotation.loaded(b)
        rotation.endVisit()
        assertThat(rotation.peek()).isSameInstanceAs(b)
        assertThat(a.destroyed).isEqualTo(0)
        assertThat(rotation.beginVisit().ad).isSameInstanceAs(b)
    }

    @Test fun `peek skips an expired ad without destroying it`() {
        showAndLeave(a)
        now = 3_600_001
        assertThat(rotation.peek()).isNull()
        assertThat(a.destroyed).isEqualTo(0)
        // The visit is still the one that destroys it.
        assertThat(rotation.beginVisit().ad).isNull()
        assertThat(a.destroyed).isEqualTo(1)
    }

    @Test fun `peek skips an expired next ad and falls back to a fresh current one`() {
        showAndLeave(a)
        now = 60_000
        rotation.beginVisit()
        rotation.loaded(b)
        rotation.endVisit()
        now = 50_000 // the clock went back before b loaded: b's age is unknown
        assertThat(rotation.peek()).isSameInstanceAs(a)
        assertThat(rotation.beginVisit().ad).isSameInstanceAs(a)
    }

    @Test fun `hiding for consent or ad-free destroys the held ads`() {
        val dropping = listOf(HideReason.CONSENT_PENDING, HideReason.CONSENT_FAILED, HideReason.CONSENT_NO_ADS, HideReason.AD_FREE)
        for (reason in dropping) {
            val rotation = NativeAdRotation(nowMillis = { now })
            val shown = FakeAd("shown")
            val kept = FakeAd("kept")
            rotation.beginVisit()
            rotation.loaded(shown)
            rotation.endVisit()
            rotation.loaded(kept)
            rotation.hidden(reason)
            assertThat(shown.destroyed, "$reason").isEqualTo(1)
            assertThat(kept.destroyed, "$reason").isEqualTo(1)
            assertThat(rotation.peek(), "$reason").isNull()
        }
    }

    @Test fun `hiding for any other reason keeps the held ads`() {
        val keeping = HideReason.entries - setOf(HideReason.CONSENT_PENDING, HideReason.CONSENT_FAILED, HideReason.CONSENT_NO_ADS, HideReason.AD_FREE)
        showAndLeave(a)
        for (reason in keeping) rotation.hidden(reason)
        assertThat(a.destroyed).isEqualTo(0)
        assertThat(rotation.peek()).isSameInstanceAs(a)
    }

    @Test fun `an ad requested before a consent hide is destroyed when it lands, never kept`() {
        rotation.beginVisit()
        rotation.endVisit()
        rotation.hidden(HideReason.CONSENT_NO_ADS)
        assertThat(rotation.loaded(a)).isNull()
        assertThat(a.destroyed).isEqualTo(1)
        assertThat(rotation.peek()).isNull()
        // The request is over: a later visit, 60 s after it, requests again.
        now = 60_000
        assertThat(rotation.beginVisit().request).isTrue()
    }

    @Test fun `a request that fails after a consent hide doesn't discard the next one`() {
        rotation.beginVisit()
        rotation.endVisit()
        rotation.hidden(HideReason.AD_FREE)
        rotation.failed()
        now = 60_000
        rotation.beginVisit()
        assertThat(rotation.loaded(a)).isSameInstanceAs(a)
        assertThat(a.destroyed).isEqualTo(0)
    }

    @Test fun `rotations are one per placement`() {
        assertThat(NativeAdRotations.of("a")).isSameInstanceAs(NativeAdRotations.of("a"))
        assertThat(NativeAdRotations.of("a")).isNotSameInstanceAs(NativeAdRotations.of("b"))
    }
}
