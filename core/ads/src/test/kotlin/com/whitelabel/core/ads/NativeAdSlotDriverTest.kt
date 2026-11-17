package com.whitelabel.core.ads

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import org.junit.jupiter.api.Test

class NativeAdSlotDriverTest {

    private var now = 0L
    private val rotation = NativeAdRotation(nowMillis = { now })

    private class FakeAd : NativeAdHandle {
        var destroyed = 0
        override fun destroy() {
            destroyed++
        }
    }

    /** One request the driver sent: its unit and the callbacks the host would call. */
    private class Request(val unitId: String, val onLoaded: (NativeAdHandle) -> Unit, val onFailed: () -> Unit)

    private val requests = mutableListOf<Request>()

    private fun driver() = NativeAdSlotDriver(rotation) { unitId, onLoaded, onFailed -> requests += Request(unitId, onLoaded, onFailed) }

    @Test fun `ready requests once and shows the loaded ad`() {
        val driver = driver()
        driver.ready("u")
        assertThat(requests.map { it.unitId }).isEqualTo(listOf("u"))
        assertThat(driver.shown).isNull()
        val ad = FakeAd()
        requests.single().onLoaded(ad)
        assertThat(driver.shown).isSameInstanceAs(ad)
    }

    @Test fun `hidden mid-visit removes the ad without destroying it, ready again shows it without a request`() {
        val driver = driver()
        driver.ready("u")
        val ad = FakeAd()
        requests.single().onLoaded(ad)
        driver.hidden(HideReason.APP_HIDDEN)
        assertThat(driver.shown).isNull()
        assertThat(ad.destroyed).isEqualTo(0)
        now = 30_000
        driver.ready("u")
        assertThat(driver.shown).isSameInstanceAs(ad)
        assertThat(requests.size).isEqualTo(1)
    }

    @Test fun `a load finishing after dispose is not shown in the disposed slot`() {
        val first = driver()
        first.ready("u")
        first.dispose()
        val ad = FakeAd()
        requests.single().onLoaded(ad)
        assertThat(first.shown).isNull()
        assertThat(ad.destroyed).isEqualTo(0)
        // It is kept for the next visit: a new slot of the placement shows it at once, without a request.
        val second = driver()
        second.ready("u")
        assertThat(second.shown).isSameInstanceAs(ad)
        assertThat(requests.size).isEqualTo(1)
    }

    @Test fun `a second ready with the same unit doesn't request again`() {
        val driver = driver()
        driver.ready("u")
        now = 120_000
        driver.ready("u")
        assertThat(requests.size).isEqualTo(1)
    }

    @Test fun `a failed load shows nothing and the next visit retries after the refresh interval`() {
        val driver = driver()
        driver.ready("u")
        requests.single().onFailed()
        assertThat(driver.shown).isNull()
        driver.hidden(HideReason.APP_HIDDEN)
        now = 60_000
        driver.ready("u")
        assertThat(requests.size).isEqualTo(2)
    }

    @Test fun `ready after dispose does nothing`() {
        val driver = driver()
        driver.dispose()
        driver.ready("u")
        assertThat(requests.size).isEqualTo(0)
        assertThat(driver.shown).isNull()
    }

    @Test fun `paused keeps the ad on the stopped screen and ends the visit, so coming back past an hour drops it`() {
        val driver = driver()
        driver.ready("u")
        val ad = FakeAd()
        requests.single().onLoaded(ad)
        driver.paused()
        assertThat(driver.shown).isSameInstanceAs(ad)
        now = 3_600_001
        driver.ready("u")
        assertThat(driver.shown).isNull()
        assertThat(ad.destroyed).isEqualTo(1)
        // A new visit: it requests a fresh ad.
        assertThat(requests.size).isEqualTo(2)
    }

    @Test fun `a load that lands while paused is kept for when the screen comes back`() {
        val driver = driver()
        driver.ready("u")
        driver.paused()
        val ad = FakeAd()
        requests.single().onLoaded(ad)
        assertThat(driver.shown).isNull()
        driver.ready("u")
        assertThat(driver.shown).isSameInstanceAs(ad)
    }

    @Test fun `a new slot of a placement with a held ad shows it before any visit begins`() {
        val first = driver()
        first.ready("u")
        val ad = FakeAd()
        requests.single().onLoaded(ad)
        first.dispose()
        now = 10_000
        val second = driver()
        second.seed()
        // Drawn on the first frame: no empty frame, no row inserted a frame later.
        assertThat(second.shown).isSameInstanceAs(ad)
        second.ready("u")
        assertThat(second.shown).isSameInstanceAs(ad)
        assertThat(requests.size).isEqualTo(1)
        assertThat(ad.destroyed).isEqualTo(0)
    }

    @Test fun `seeding a slot with nothing held shows nothing and requests nothing`() {
        val driver = driver()
        driver.seed()
        assertThat(driver.shown).isNull()
        assertThat(requests.size).isEqualTo(0)
    }

    @Test fun `seeding skips an expired ad, and the visit then destroys it and requests`() {
        val first = driver()
        first.ready("u")
        val ad = FakeAd()
        requests.single().onLoaded(ad)
        first.dispose()
        now = 3_600_001
        val second = driver()
        second.seed()
        assertThat(second.shown).isNull()
        assertThat(ad.destroyed).isEqualTo(0)
        second.ready("u")
        assertThat(ad.destroyed).isEqualTo(1)
        assertThat(requests.size).isEqualTo(2)
    }

    @Test fun `hidden for ad-free or consent removes and destroys the held ad`() {
        val driver = driver()
        driver.ready("u")
        val ad = FakeAd()
        requests.single().onLoaded(ad)
        driver.hidden(HideReason.AD_FREE)
        assertThat(driver.shown).isNull()
        assertThat(ad.destroyed).isEqualTo(1)
        now = 60_000
        driver.ready("u")
        assertThat(driver.shown).isNull()
        assertThat(requests.size).isEqualTo(2)
    }

    @Test fun `a request discarded by a consent hide neither blocks nor fills the next visit`() {
        val driver = driver()
        driver.ready("u")
        driver.hidden(HideReason.CONSENT_NO_ADS)
        now = 60_000
        driver.ready("u")
        assertThat(requests.size).isEqualTo(2)
        // The discarded request lands late: destroyed, not shown; the new one shows.
        val stale = FakeAd()
        requests[0].onLoaded(stale)
        assertThat(stale.destroyed).isEqualTo(1)
        assertThat(driver.shown).isNull()
        val fresh = FakeAd()
        requests[1].onLoaded(fresh)
        assertThat(driver.shown).isSameInstanceAs(fresh)
    }
}
