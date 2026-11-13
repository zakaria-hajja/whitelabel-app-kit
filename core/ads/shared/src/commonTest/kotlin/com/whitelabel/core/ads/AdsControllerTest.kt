package com.whitelabel.core.ads

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isSameInstanceAs
import com.whitelabel.core.consent.Consent
import com.whitelabel.core.consent.FakeConsentPort
import com.whitelabel.core.consent.UmpResult
import com.whitelabel.core.consent.UmpStatus
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.ForwardingFileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.Source
import okio.fakefilesystem.FakeFileSystem

// DataStore keeps a process-wide registry by path, so every test gets its own path.
private var nextDir = 0

private class RecordingTracking : TrackingPort {
    var calls = 0
    val pending = mutableListOf<() -> Unit>()
    override fun requestIfNeeded(onDone: () -> Unit) {
        calls++
        pending += onDone
    }
    fun finish() = pending.toList().also { pending.clear() }.forEach { it() }
}

private val granted = UmpResult(UmpStatus.NOT_REQUIRED, canRequestAds = true, purposeConsents = null)
private val denied = UmpResult(UmpStatus.REQUIRED, canRequestAds = false, purposeConsents = null)

class AdsControllerTest {
    private val fileSystem = FakeFileSystem()
    private val dir = "/data/controller-${nextDir++}"
    private var now = Instant.fromEpochSeconds(10_000)
    private val clock = object : Clock { override fun now() = now }
    private val units = mapOf("home" to "unit-home", "detail" to "unit-detail")

    private fun TestScope.scope(job: Job = Job()) = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + job)

    private class Rig(
        val port: FakeConsentPort,
        val consent: Consent,
        val adFree: AdFree,
        val tracking: RecordingTracking,
        val controller: AdsController,
    )

    /** [earlierLaunches] processes ran before this one, so this launch is number earlierLaunches + 1. */
    private suspend fun TestScope.rig(
        earlierLaunches: Int,
        enabled: Boolean = true,
        port: FakeConsentPort = FakeConsentPort(granted),
        gather: Boolean = true,
        adFreeScope: CoroutineScope = scope(),
    ): Rig {
        val launchPath = "$dir/ads-launches.json".toPath()
        repeat(earlierLaunches) {
            val process = Job()
            LaunchCounter(launchPath, scope(process), fileSystem).countThisLaunch()
            process.cancel()
        }
        val consent = Consent(port)
        if (gather) consent.gatherOnLaunch()
        val adFree = AdFree("$dir/ads-free.json".toPath(), clock, adFreeScope, fileSystem)
        val tracking = RecordingTracking()
        val controller = AdsController(
            enabled, units, consent, LaunchCounter(launchPath, scope(), fileSystem), adFree, tracking, clock, scope(),
        )
        return Rig(port, consent, adFree, tracking, controller)
    }

    private fun TestScope.advance(by: Duration) {
        now += by
        testScheduler.advanceTimeBy(by)
        testScheduler.runCurrent()
    }

    private fun hidden(reason: HideReason) = BannerState.Hidden(reason)
    private val ready = BannerState.Ready("unit-home")

    @Test
    fun `hidden until consent resolves and then ready on launch two`() = runTest {
        val rig = rig(earlierLaunches = 1, gather = false)
        val banner = rig.controller.bannerState("home")
        assertThat(banner.value).isEqualTo(hidden(HideReason.CONSENT_PENDING))
        rig.consent.gatherOnLaunch()
        rig.tracking.finish()
        assertThat(banner.value).isEqualTo(ready)
    }

    @Test
    fun `launch 1 never shows`() = runTest {
        val rig = rig(earlierLaunches = 0)
        val banner = rig.controller.bannerState("home")
        rig.tracking.finish()
        assertThat(banner.value).isEqualTo(hidden(HideReason.FIRST_LAUNCH))
        assertThat(rig.tracking.calls).isEqualTo(0)
    }

    @Test
    fun `ads off is hidden`() = runTest {
        val rig = rig(earlierLaunches = 1, enabled = false)
        assertThat(rig.controller.bannerState("home").value).isEqualTo(hidden(HideReason.ADS_OFF))
    }

    @Test
    fun `ad-free hides and ready returns when it expires`() = runTest {
        val rig = rig(earlierLaunches = 1)
        rig.adFree.grantUntil(now + 1.hours)
        val banner = rig.controller.bannerState("home")
        assertThat(banner.value).isEqualTo(hidden(HideReason.AD_FREE))
        advance(1.hours)
        rig.tracking.finish()
        assertThat(banner.value).isEqualTo(ready)
    }

    @Test
    fun `not loaded yet hides even when everything else says show`() = runTest {
        // A store that never delivers (its scope never runs): the persisted window may be active, so no ad.
        val rig = rig(earlierLaunches = 1, adFreeScope = CoroutineScope(StandardTestDispatcher(testScheduler)))
        val banner = rig.controller.bannerState("home")
        assertThat(banner.value).isEqualTo(hidden(HideReason.LOADING))
        assertThat(rig.tracking.calls).isEqualTo(0)
    }

    @Test
    fun `consent failing mid-launch hides`() = runTest {
        val rig = rig(earlierLaunches = 1)
        val banner = rig.controller.bannerState("home")
        rig.tracking.finish()
        assertThat(banner.value).isEqualTo(ready)
        rig.port.next = denied
        rig.consent.showPrivacyOptions()
        assertThat(banner.value).isEqualTo(hidden(HideReason.CONSENT_FAILED))
    }

    @Test
    fun `tracking is not requested before consent resolves`() = runTest {
        val early = rig(earlierLaunches = 1, gather = false)
        early.controller.bannerState("home")
        assertThat(early.tracking.calls).isEqualTo(0)
    }

    @Test
    fun `tracking is requested once before the first ready`() = runTest {
        val rig = rig(earlierLaunches = 1)
        val banner = rig.controller.bannerState("home")
        assertThat(rig.tracking.calls).isEqualTo(1)
        // The prompt is still open: not ready yet.
        assertThat(banner.value).isEqualTo(hidden(HideReason.LOADING))
        rig.tracking.finish()
        assertThat(banner.value).isEqualTo(ready)
        // Hide and show again in the same launch: tracking is not asked a second time.
        rig.port.next = denied
        rig.consent.showPrivacyOptions()
        rig.port.next = granted
        rig.consent.showPrivacyOptions()
        assertThat(banner.value).isEqualTo(ready)
        assertThat(rig.tracking.calls).isEqualTo(1)
    }

    @Test
    fun `two collectors of one placement share one tracking request`() = runTest {
        val rig = rig(earlierLaunches = 1)
        val a = rig.controller.bannerState("home")
        val b = rig.controller.bannerState("home")
        assertThat(a).isSameInstanceAs(b)
        rig.controller.bannerState("detail")
        assertThat(rig.tracking.calls).isEqualTo(1)
        rig.tracking.finish()
        assertThat(a.value).isEqualTo(ready)
        assertThat(rig.controller.bannerState("detail").value).isEqualTo(BannerState.Ready("unit-detail"))
    }

    @Test
    fun `an undeclared placement is hidden`() = runTest {
        val rig = rig(earlierLaunches = 1)
        val banner = rig.controller.bannerState("nowhere")
        assertThat(banner.value).isEqualTo(hidden(HideReason.UNDECLARED))
        assertThat(rig.tracking.calls).isEqualTo(0)
    }

    @Test
    fun `two undeclared names share one constant flow`() = runTest {
        val rig = rig(earlierLaunches = 1)
        val a = rig.controller.bannerState("x")
        assertThat(a).isSameInstanceAs(rig.controller.bannerState("y"))
        assertThat(a.value).isEqualTo(hidden(HideReason.UNDECLARED))
    }

    @Test
    fun `a failing launch store does not crash and banners stay hidden`() = runTest {
        val failing = object : ForwardingFileSystem(fileSystem) {
            override fun sink(file: Path, mustCreate: Boolean): Sink = throw IOException("disk")
            override fun source(file: Path): Source = throw IOException("disk")
            override fun appendingSink(file: Path, mustExist: Boolean): Sink = throw IOException("disk")
            override fun atomicMove(source: Path, target: Path) = throw IOException("disk")
        }
        val consent = Consent(FakeConsentPort(granted)).also { it.gatherOnLaunch() }
        val controller = AdsController(
            true, units, consent, LaunchCounter("$dir/ads-launches.json".toPath(), scope(), failing),
            AdFree("$dir/ads-free.json".toPath(), clock, scope(), fileSystem), RecordingTracking(), clock, scope(),
        )
        assertThat(controller.bannerState("home").value).isEqualTo(hidden(HideReason.FIRST_LAUNCH))
    }
}
