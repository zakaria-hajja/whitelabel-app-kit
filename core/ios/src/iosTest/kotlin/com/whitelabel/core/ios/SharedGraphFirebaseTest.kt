package com.whitelabel.core.ios

import com.whitelabel.core.analytics.Analytics
import com.whitelabel.core.analytics.AnalyticsEvent
import com.whitelabel.core.analytics.CrashReporter
import com.whitelabel.core.consent.ConsentState
import com.whitelabel.core.consent.UmpResult
import com.whitelabel.core.consent.UmpStatus
import com.whitelabel.core.domain.remote.RemoteValues
import com.whitelabel.core.firebase.CollectionSwitch
import com.whitelabel.core.firebase.FakeRemoteConfigSource
import com.whitelabel.core.firebase.FirebaseCollection
import com.whitelabel.core.firebase.FirebaseMode
import com.whitelabel.core.shell.model.DefinitionSpec
import com.whitelabel.core.shell.model.FeatureSpec
import com.whitelabel.feature.settings.SettingsRoutes
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okio.FileSystem

/** How the graph decides its Firebase mode and binds the services Swift hands over. */
@OptIn(ExperimentalCoroutinesApi::class)
class SharedGraphFirebaseTest {

    private val dir = testDir()
    private val settingsOnly = DefinitionSpec(listOf(FeatureSpec("settings", null, SettingsRoutes.ROOT, false, false, false)), null)

    private class Noop : Analytics, CrashReporter {
        override fun track(event: AnalyticsEvent) = Unit
        override fun record(throwable: Throwable) = Unit
    }

    private class Switch : CollectionSwitch {
        override fun apply(collection: FirebaseCollection) = Unit
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

    private fun start(firebase: String) =
        SharedGraph.start(testPlatform(dir, firebase = firebase), settingsOnly, emptyList(), useMockApi = false)

    private fun values(): RemoteValues = SharedGraph.remoteValues()

    @Test fun `on without registered services fails at start`() {
        val error = assertFailsWith<IllegalStateException> { start("on") }
        assertEquals("firebase is on but no Firebase service was installed", error.message)
    }

    @Test fun `on with the services keeps the mode on`() {
        SharedGraph.useFirebase(FakeRemoteConfigSource(mapOf("k" to "v")), Noop(), Noop(), Switch())
        start("on")
        assertEquals(FirebaseMode.ON, SharedGraph.firebaseMode())
        assertEquals("v", values().string("k", "d"))
    }

    @Test fun `fake mode serves the fake remote value`() {
        SharedGraph.declareFirebase()
        start("fake")
        assertEquals(FirebaseMode.FAKE, SharedGraph.firebaseMode())
        assertEquals("from the fake", values().string("showcase_remote_note", "default"))
    }

    @Test fun `the fake debug marker wins over registered services and the fake consent obtains by default`() {
        SharedGraph.useFirebase(FakeRemoteConfigSource(mapOf("showcase_remote_note" to "real")), Noop(), Noop(), Switch())
        SharedGraph.useFirebaseDebug(fake = true, analytics = false, fakeConsent = "obtained")
        start("on")
        assertEquals(FirebaseMode.FAKE, SharedGraph.firebaseMode())
        assertEquals("from the fake", values().string("showcase_remote_note", "default"))
        SharedGraph.consent().gatherOnLaunch()
        assertIs<ConsentState.Resolved>(SharedGraph.consent().state.value)
    }

    @Test fun `the fake debug marker without a service stays off`() {
        SharedGraph.useFirebaseDebug(fake = true, analytics = false, fakeConsent = "obtained")
        start("off")
        assertEquals(FirebaseMode.OFF, SharedGraph.firebaseMode())
    }

    @Test fun `fake consent failed makes the gather fail`() {
        SharedGraph.declareFirebase()
        SharedGraph.useFirebaseDebug(fake = true, analytics = false, fakeConsent = "failed")
        start("off")
        SharedGraph.consent().gatherOnLaunch()
        assertEquals(ConsentState.Failed, SharedGraph.consent().state.value)
    }

    @Test fun `off keeps defaults-only remote values`() {
        SharedGraph.declareFirebase()
        start("off")
        assertEquals(FirebaseMode.OFF, SharedGraph.firebaseMode())
        assertEquals("default", values().string("showcase_remote_note", "default"))
    }

    @Test fun `fake consent states map as on android`() {
        assertEquals(UmpResult(UmpStatus.OBTAINED, canRequestAds = true, purposeConsents = "1111111111"), fakeConsentResult("obtained"))
        assertEquals(UmpResult(UmpStatus.OBTAINED, canRequestAds = true, purposeConsents = "0"), fakeConsentResult("declined"))
        assertEquals(UmpResult(UmpStatus.NOT_REQUIRED, canRequestAds = true, purposeConsents = null), fakeConsentResult("notRequired"))
        assertNull(fakeConsentResult("failed"))
        assertTrue(fakeConsentResult("nonsense") == fakeConsentResult("obtained"))
    }
}
