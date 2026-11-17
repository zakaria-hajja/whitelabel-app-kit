package com.whitelabel.core.ads

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEqualTo
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

    /** The id of the latest request a [visit] started; a host's callback hands it back. */
    private var lastId = 0

    private fun visit(): NativeAdVisit = rotation.beginVisit().also { if (it.request) lastId = it.requestId }

    /** A visit that requests, loads [ad] while shown, and ends. */
    private fun showAndLeave(ad: FakeAd) {
        visit()
        rotation.loaded(ad, lastId)
        rotation.endVisit()
    }

    @Test fun `first visit requests and shows nothing`() {
        assertThat(visit()).isEqualTo(NativeAdVisit(null, true, requestId = 1))
    }

    @Test fun `loaded during the visit shows at once`() {
        visit()
        assertThat(rotation.loaded(a, lastId)).isSameInstanceAs(a)
    }

    @Test fun `a visit within the refresh interval reuses the ad without a request`() {
        showAndLeave(a)
        now = 30_000
        assertThat(visit()).isEqualTo(NativeAdVisit(a, false))
    }

    @Test fun `after the refresh interval the shown ad stays and a replacement is requested`() {
        showAndLeave(a)
        now = 61_000
        assertThat(visit()).isEqualTo(NativeAdVisit(a, true, requestId = 2))
        assertThat(rotation.loaded(b, lastId)).isNull()
        assertThat(a.destroyed).isEqualTo(0)
    }

    @Test fun `the replacement shows on the next visit and the old ad is destroyed`() {
        showAndLeave(a)
        now = 61_000
        visit()
        rotation.loaded(b, lastId)
        rotation.endVisit()
        val visit = visit()
        assertThat(visit).isEqualTo(NativeAdVisit(b, false))
        assertThat(a.destroyed).isEqualTo(1)
        assertThat(b.destroyed).isEqualTo(0)
    }

    @Test fun `loaded after the visit ended is kept for the next visit`() {
        visit()
        rotation.endVisit()
        assertThat(rotation.loaded(a, lastId)).isNull()
        assertThat(visit().ad).isSameInstanceAs(a)
        assertThat(a.destroyed).isEqualTo(0)
    }

    @Test fun `a replaced next is destroyed`() {
        showAndLeave(a)
        now = 61_000
        visit()
        rotation.loaded(b, lastId)
        // A second load while b waits (the host delivered twice, or a late callback): b gives way to c.
        rotation.loaded(c, lastId)
        assertThat(b.destroyed).isEqualTo(1)
        rotation.endVisit()
        assertThat(visit().ad).isSameInstanceAs(c)
        assertThat(a.destroyed).isEqualTo(1)
    }

    @Test fun `an ad older than an hour is destroyed and not shown`() {
        showAndLeave(a)
        now = 3_600_001
        assertThat(visit()).isEqualTo(NativeAdVisit(null, true, requestId = 2))
        assertThat(a.destroyed).isEqualTo(1)
    }

    @Test fun `an ad exactly an hour old still shows`() {
        showAndLeave(a)
        now = 3_600_000
        assertThat(visit().ad).isSameInstanceAs(a)
    }

    @Test fun `a kept next older than an hour is destroyed and not promoted`() {
        visit()
        rotation.endVisit()
        rotation.loaded(a, lastId)
        now = 3_600_001
        assertThat(visit()).isEqualTo(NativeAdVisit(null, true, requestId = 2))
        assertThat(a.destroyed).isEqualTo(1)
    }

    @Test fun `a failed load waits the refresh interval`() {
        visit()
        rotation.failed(lastId)
        rotation.endVisit()
        now = 10_000
        assertThat(visit().request).isFalse()
        rotation.endVisit()
        now = 60_000
        assertThat(visit().request).isTrue()
    }

    @Test fun `no second request while one is in flight`() {
        visit()
        rotation.endVisit()
        now = 120_000
        assertThat(visit().request).isFalse()
    }

    @Test fun `a clock that went back before the load expires the ad`() {
        now = 100_000
        showAndLeave(a)
        now = 50_000
        // The age can't be known any more; an ad possibly over an hour old is never shown.
        assertThat(visit().ad).isNull()
        assertThat(a.destroyed).isEqualTo(1)
    }

    @Test fun `a clock that went back before the last request doesn't block requests`() {
        now = 100_000
        visit()
        rotation.failed(lastId)
        rotation.endVisit()
        now = 50_000
        assertThat(visit().request).isTrue()
    }

    @Test fun `peek is empty before anything loaded`() {
        assertThat(rotation.peek()).isNull()
    }

    @Test fun `peek gives the held ad a visit would show without beginning one`() {
        showAndLeave(a)
        now = 30_000
        assertThat(rotation.peek()).isSameInstanceAs(a)
        // Peeking neither began a visit nor used up the request: the visit still decides as before.
        assertThat(rotation.loaded(b, lastId)).isNull()
        assertThat(rotation.peek()).isSameInstanceAs(b)
        assertThat(a.destroyed).isEqualTo(0)
    }

    @Test fun `peek gives the next ad over the current one as the next visit promotes it`() {
        showAndLeave(a)
        now = 60_000
        visit()
        rotation.loaded(b, lastId)
        rotation.endVisit()
        assertThat(rotation.peek()).isSameInstanceAs(b)
        assertThat(a.destroyed).isEqualTo(0)
        assertThat(visit().ad).isSameInstanceAs(b)
    }

    @Test fun `peek skips an expired ad without destroying it`() {
        showAndLeave(a)
        now = 3_600_001
        assertThat(rotation.peek()).isNull()
        assertThat(a.destroyed).isEqualTo(0)
        // The visit is still the one that destroys it.
        assertThat(visit().ad).isNull()
        assertThat(a.destroyed).isEqualTo(1)
    }

    @Test fun `peek skips an expired next ad and falls back to a fresh current one`() {
        showAndLeave(a)
        now = 60_000
        visit()
        rotation.loaded(b, lastId)
        rotation.endVisit()
        now = 50_000 // the clock went back before b loaded: b's age is unknown
        assertThat(rotation.peek()).isSameInstanceAs(a)
        assertThat(visit().ad).isSameInstanceAs(a)
    }

    @Test fun `hiding for consent or ad-free destroys the held ads`() {
        val dropping = listOf(HideReason.CONSENT_PENDING, HideReason.CONSENT_FAILED, HideReason.CONSENT_NO_ADS, HideReason.AD_FREE)
        for (reason in dropping) {
            val rotation = NativeAdRotation(nowMillis = { now })
            val shown = FakeAd("shown")
            val kept = FakeAd("kept")
            val first = visit()
            rotation.loaded(shown, first.requestId)
            rotation.endVisit()
            // A duplicate delivery of the same request is kept as next.
            rotation.loaded(kept, first.requestId)
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

    @Test fun `an ad requested before a consent hide is destroyed when it lands and never kept`() {
        visit()
        rotation.endVisit()
        rotation.hidden(HideReason.CONSENT_NO_ADS)
        assertThat(rotation.loaded(a, lastId)).isNull()
        assertThat(a.destroyed).isEqualTo(1)
        assertThat(rotation.peek()).isNull()
        // The request is over: a later visit, 60 s after it, requests again.
        now = 60_000
        assertThat(visit().request).isTrue()
    }

    @Test fun `a request that fails after a consent hide doesn't discard the next one`() {
        visit()
        rotation.endVisit()
        rotation.hidden(HideReason.AD_FREE)
        rotation.failed(lastId)
        now = 60_000
        visit()
        assertThat(rotation.loaded(a, lastId)).isSameInstanceAs(a)
        assertThat(a.destroyed).isEqualTo(0)
    }

    @Test fun `a visit after a consent hide requests again while the discarded request still loads`() {
        val stale = visit()
        rotation.endVisit()
        rotation.hidden(HideReason.CONSENT_NO_ADS)
        now = 60_000
        val visit = visit()
        assertThat(visit.request).isTrue()
        assertThat(visit.requestId).isNotEqualTo(stale.requestId)
        // The discarded request lands late: destroyed, and it doesn't take the new request's place.
        assertThat(rotation.loaded(a, stale.requestId)).isNull()
        assertThat(a.destroyed).isEqualTo(1)
        assertThat(rotation.loaded(b, visit.requestId)).isSameInstanceAs(b)
        assertThat(b.destroyed).isEqualTo(0)
    }

    @Test fun `a discarded request failing late doesn't end the new one`() {
        val stale = visit()
        rotation.endVisit()
        rotation.hidden(HideReason.AD_FREE)
        now = 60_000
        val visit = visit()
        rotation.failed(stale.requestId)
        rotation.endVisit()
        now = 120_000
        // The new request is still loading: no second one.
        assertThat(visit().request).isFalse()
        assertThat(rotation.loaded(a, visit.requestId)).isSameInstanceAs(a)
    }

    @Test fun `rotations are one per placement`() {
        assertThat(NativeAdRotations.of("a")).isSameInstanceAs(NativeAdRotations.of("a"))
        assertThat(NativeAdRotations.of("a")).isNotSameInstanceAs(NativeAdRotations.of("b"))
    }
}
