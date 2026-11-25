package com.whitelabel.core.ios

import com.whitelabel.core.analytics.Analytics
import com.whitelabel.core.analytics.AnalyticsEvent
import com.whitelabel.core.analytics.CrashReporter
import com.whitelabel.core.analytics.UserProperty
import com.whitelabel.core.consent.ConsentState
import com.whitelabel.core.consent.FakeConsentPort
import com.whitelabel.core.consent.UmpResult
import com.whitelabel.core.consent.UmpStatus
import com.whitelabel.core.firebase.CollectionSwitch
import com.whitelabel.core.firebase.FakeRemoteConfigSource
import com.whitelabel.core.firebase.FirebaseCollection
import com.whitelabel.core.shell.model.DefinitionSpec
import com.whitelabel.core.shell.model.FeatureSpec
import com.whitelabel.feature.settings.SettingsRoutes
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okio.FileSystem
import org.koin.core.qualifier.named
import org.koin.mp.KoinPlatform

/** `SharedGraph.useAnalyticsOptIn(enabled:)` (Swift: `FirebaseService(analyticsOptIn: true)`): a decliner sends nothing. */
@OptIn(ExperimentalCoroutinesApi::class)
class SharedGraphStrictOptInTest {

    private val dir = testDir()
    private val settingsOnly = DefinitionSpec(listOf(FeatureSpec("settings", null, SettingsRoutes.ROOT, false, false, false)), null)

    // Purposes "0": no storage purpose, so analytics is declined.
    private val declined = UmpResult(UmpStatus.OBTAINED, true, "0")

    private class Recording : Analytics, CrashReporter {
        private val sent = MutableStateFlow(emptyList<String>())
        val log: List<String> get() = sent.value
        override fun track(event: AnalyticsEvent) = sent.update { it + "event ${event.name}" }
        override fun setUserProperty(property: UserProperty) = sent.update { it + "property ${property.name}" }
        override fun record(throwable: Throwable) = Unit
    }

    private class Switch : CollectionSwitch {
        val applied = MutableStateFlow<FirebaseCollection?>(null)
        override fun apply(collection: FirebaseCollection) { applied.value = collection }
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

    /** Starts in `on` mode, declines, waits until the gates follow the answer, then sends one of each. */
    private fun declineAndSend(analyticsOptIn: Boolean?): Pair<FirebaseCollection?, List<String>> {
        val inner = Recording()
        val switch = Switch()
        if (analyticsOptIn != null) SharedGraph.useAnalyticsOptIn(analyticsOptIn)
        SharedGraph.useConsent(FakeConsentPort(declined))
        SharedGraph.useFirebase(FakeRemoteConfigSource(mapOf("k" to "v")), inner, inner, switch)
        SharedGraph.start(testPlatform(dir, firebase = "on"), settingsOnly, emptyList(), useMockApi = false)
        val analytics = KoinPlatform.getKoin().get<Analytics>()
        analytics.setUserProperty(UserProperty("bucket", "queued"))
        SharedGraph.consent().gatherOnLaunch()
        // The applier runs on the graph's scope, off this thread: wait for the consent it applied.
        val applied = KoinPlatform.getKoin().get<StateFlow<ConsentState>>(named("firebaseAppliedConsent"))
        runBlocking { withTimeout(5.seconds) { applied.first { it is ConsentState.Resolved } } }
        analytics.track(AnalyticsEvent("after"))
        analytics.setUserProperty(UserProperty("streak", "after"))
        return switch.applied.value to inner.log
    }

    @Test fun `with strict opt-in a decline turns analytics off and sends nothing`() {
        val (collection, sent) = declineAndSend(analyticsOptIn = true)
        assertEquals(FirebaseCollection(analytics = false, analyticsStorage = false, crashes = true), collection)
        assertEquals(emptyList(), sent)
    }

    @Test fun `by default a decliner is still counted without identifiers`() {
        val (collection, sent) = declineAndSend(analyticsOptIn = null)
        assertEquals(FirebaseCollection(analytics = true, analyticsStorage = false, crashes = true), collection)
        assertEquals(listOf("event after", "property streak"), sent)
    }

    @Test fun `clearing the services turns strict opt-in off again`() {
        SharedGraph.useAnalyticsOptIn(true)
        SharedGraph.clearFirebaseServices()
        val (collection, _) = declineAndSend(analyticsOptIn = null)
        assertEquals(true, collection?.analytics)
    }
}
