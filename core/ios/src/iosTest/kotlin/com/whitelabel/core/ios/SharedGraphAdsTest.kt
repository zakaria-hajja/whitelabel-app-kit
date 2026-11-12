package com.whitelabel.core.ios

import com.whitelabel.core.ads.AdPlacement
import com.whitelabel.core.ads.BannerState
import com.whitelabel.core.ads.HideReason
import com.whitelabel.core.ads.LaunchCounter
import com.whitelabel.core.ads.TrackingPort
import com.whitelabel.core.consent.ConsentState
import com.whitelabel.core.consent.FakeConsentPort
import com.whitelabel.core.consent.UmpResult
import com.whitelabel.core.consent.UmpStatus
import com.whitelabel.core.shell.model.DefinitionSpec
import com.whitelabel.core.shell.model.FeatureSpec
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds
import okio.FileSystem
import org.koin.mp.KoinPlatform

/** How the graph checks the app's ads against its settings and binds what Swift hands over. */
@OptIn(ExperimentalCoroutinesApi::class)
class SharedGraphAdsTest {

    private val dir = testDir()
    private val unit = "ca-app-pub-6543210987654321/0987654321"
    private val testUnit = "ca-app-pub-3940256099942544/2435281174"

    private val definition = DefinitionSpec(
        listOf(
            FeatureSpec("auth", null, null, false, true, false),
            FeatureSpec("home", null, "home", true, false, false),
            FeatureSpec("settings", null, "settings", false, false, false),
        ),
        null,
    )

    private class CountingTracking : TrackingPort {
        var requests = 0
        override fun requestIfNeeded(onDone: () -> Unit) {
            requests++
            onDone()
        }
    }

    @BeforeTest fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        SharedGraph.clearFirebaseServices()
    }

    @AfterTest fun tearDown() {
        SharedGraph.stop()
        SharedGraph.clearFirebaseServices()
        FileSystem.SYSTEM.deleteRecursively(dir)
        Dispatchers.resetMain()
    }

    private fun start(ads: String = "on", units: String = "home=$unit", declared: Boolean = true, debugBuild: Boolean = false) =
        SharedGraph.start(
            testPlatform(dir, ads = ads, adsUnits = units, adsDeclared = declared, debugBuild = debugBuild),
            definition, emptyList(), useMockApi = false,
        )

    private companion object {
        const val AUTH_START = "auth/login"
    }

    /** The store reads and the launch count run on the graph's own scope, off this thread. */
    private fun awaitBanner(expected: BannerState) = runBlocking {
        withTimeout(5.seconds) { assertNotNull(SharedGraph.adsController()).bannerState("home").first { it == expected } }
    }

    private fun placements(vararg placements: AdPlacement) = SharedGraph.declareAds(placements.toList())

    @Test fun `an app without ads has no controller and no consent`() {
        start(ads = "off", units = "", declared = false)
        assertNull(SharedGraph.adsController())
        assertNull(SharedGraph.adFree())
        assertNull(SharedGraph.consentOrNull())
    }

    @Test fun `placements without the setting fail at start`() {
        placements(AdPlacement("home", "home"))
        val error = assertFailsWith<IllegalStateException> { start(ads = "off", units = "", declared = false) }
        assertEquals("ads { } needs ads.enabled=true or debug in app.properties", error.message)
    }

    @Test fun `the setting without placements fails at start also where this build has ads off`() {
        listOf("on", "off").forEach { mode ->
            val error = assertFailsWith<IllegalStateException> { start(ads = mode, units = "") }
            assertEquals("ads.enabled is set but the app definition doesn't call ads { }", error.message)
        }
    }

    @Test fun `on without the ads service fails at start`() {
        placements(AdPlacement("home", "home"))
        val error = assertFailsWith<IllegalStateException> { start() }
        assertEquals("ads are on but AdsService() isn't in the app's services", error.message)
    }

    @Test fun `a placement under settings or the gate or not a tab or a bad name fails at start`() {
        placements(AdPlacement("s", "settings"))
        assertEquals(
            "No ads under settings: settings and sign-in screens stay ad-free",
            assertFailsWith<IllegalArgumentException> { start() }.message,
        )
        SharedGraph.clearFirebaseServices()
        // Swift names the routes the gate's screens own (Auth: sign-in, registration, …).
        SharedGraph.declareAds(listOf(AdPlacement("g", AUTH_START)), gateRoutes = listOf(AUTH_START))
        assertEquals(
            "No ads under auth: settings and sign-in screens stay ad-free",
            assertFailsWith<IllegalArgumentException> { start() }.message,
        )
        SharedGraph.clearFirebaseServices()
        placements(AdPlacement("x", "nowhere"))
        assertEquals("placement 'x' is under nowhere, which isn't a tab", assertFailsWith<IllegalArgumentException> { start() }.message)
        SharedGraph.clearFirebaseServices()
        placements(AdPlacement("home", null), AdPlacement("home", null))
        assertEquals("placement 'home' is declared twice", assertFailsWith<IllegalArgumentException> { start() }.message)
        SharedGraph.clearFirebaseServices()
        placements(AdPlacement("1st", null))
        assertEquals(
            "placement '1st' must start with a letter and use only letters, digits, '_' and '-'",
            assertFailsWith<IllegalArgumentException> { start() }.message,
        )
    }

    @Test fun `a unit without a placement fails and release needs every placement's unit`() {
        placements(AdPlacement("home", "home"))
        SharedGraph.useAds(CountingTracking())
        assertEquals(
            "ads.ios.banner.other has no matching ads { placement(\"other\") }",
            assertFailsWith<IllegalStateException> { start(units = "home=$unit;other=$unit") }.message,
        )
        assertEquals("placement 'home' has no ads.ios.banner.home", assertFailsWith<IllegalStateException> { start(units = "") }.message)
    }

    @Test fun `off in this build binds a controller that hides every placement and consent resolves without a form`() {
        placements(AdPlacement("home", "home"))
        start(ads = "off", units = "")
        val controller = assertNotNull(SharedGraph.adsController())
        assertEquals(BannerState.Hidden(HideReason.ADS_OFF), controller.bannerState("home").value)
        assertNotNull(SharedGraph.adFree())
        val consent = assertNotNull(SharedGraph.consentOrNull())
        consent.gatherOnLaunch()
        assertEquals(ConsentState.Resolved(canRequestAds = false, analyticsAllowed = false), consent.state.value)
    }

    @Test fun `debug gives a placement without a unit the test unit and undeclared ones stay undeclared`() {
        placements(AdPlacement("home", "home"))
        SharedGraph.useAds(CountingTracking())
        start(units = "", debugBuild = true)
        val controller = assertNotNull(SharedGraph.adsController())
        assertEquals(BannerState.Hidden(HideReason.UNDECLARED), controller.bannerState("other").value)
        assertEquals(testUnit, SharedGraph.adsUnits()["home"])
    }

    @Test fun `on gathers consent through the handed-over port and asks for tracking before the first banner`() {
        placements(AdPlacement("home", "home"))
        val tracking = CountingTracking()
        SharedGraph.useConsent(FakeConsentPort(UmpResult(UmpStatus.OBTAINED, canRequestAds = true, purposeConsents = "1")))
        SharedGraph.useAds(tracking)
        start()
        val consent = assertNotNull(SharedGraph.consentOrNull())
        consent.gatherOnLaunch()
        assertTrue(consent.state.value is ConsentState.Resolved)
        // Launch 1 never shows a banner, so the prompt waits for a later launch.
        awaitBanner(BannerState.Hidden(HideReason.FIRST_LAUNCH))
        // Launch 0 (not counted yet) also reads as a first launch: wait until this one is counted.
        runBlocking { withTimeout(5.seconds) { KoinPlatform.getKoin().get<LaunchCounter>().launchNumber.first { it == 1 } } }
        assertEquals(0, tracking.requests)
        // A restarted graph (the second launch) keeps the ads and the port, and asks once.
        SharedGraph.resetForTests()
        SharedGraph.consent().gatherOnLaunch()
        awaitBanner(BannerState.Ready(unit))
        assertEquals(1, tracking.requests)
    }
}
