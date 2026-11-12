package com.whitelabel.core.firebase

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.whitelabel.core.analytics.Analytics
import com.whitelabel.core.analytics.CrashReporter
import com.whitelabel.core.consent.Consent
import com.whitelabel.core.consent.ConsentPort
import com.whitelabel.core.consent.ConsentState
import com.whitelabel.core.consent.FakeConsentPort
import com.whitelabel.core.consent.UmpResult
import com.whitelabel.core.consent.UmpStatus
import com.whitelabel.core.consent.consentModule
import com.whitelabel.core.domain.remote.RemoteValues
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.koin.core.KoinApplication
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test

class FirebaseKoinTest {
    private class RecordingSwitch : CollectionSwitch {
        val applied = mutableListOf<FirebaseCollection>()
        override fun apply(collection: FirebaseCollection) { applied += collection }
    }

    private class NoopAnalytics : Analytics, CrashReporter {
        override fun track(event: com.whitelabel.core.analytics.AnalyticsEvent) = Unit
        override fun record(throwable: Throwable) = Unit
    }

    private val none = FirebaseCollection(analytics = false, analyticsStorage = false, crashes = false)

    private fun app(
        mode: FirebaseMode,
        switch: RecordingSwitch = RecordingSwitch(),
        port: ConsentPort? = null,
        override: CollectionSwitch? = null,
        debugBuild: Boolean = false,
        debugAnalytics: Boolean = false,
    ): KoinApplication =
        koinApplication {
            modules(
                module {
                    single<CoroutineScope>(named("applicationScope")) { TestScope(UnconfinedTestDispatcher()) }
                    if (port != null) single<ConsentPort> { port }
                    single<CollectionSwitch> { override ?: switch }
                    single<RemoteConfigSource> { FakeRemoteConfigSource(mapOf("k" to "v")) }
                    single<Analytics>(named("firebase")) { NoopAnalytics() }
                    single<CrashReporter>(named("firebase")) { NoopAnalytics() }
                },
                consentModule(),
                firebaseModule(mode, debugBuild, debugAnalytics),
            )
        }.also { it.createEagerInstances() } // startKoin does this; koinApplication does not

    @Test fun `off binds nothing`() {
        val koin = koinApplication { modules(firebaseModule(FirebaseMode.OFF, false, false)) }.koin
        assertThat(koin.getOrNull<RemoteValues>()).isEqualTo(null)
        assertThat(koin.getOrNull<Analytics>()).isEqualTo(null)
    }

    @Test fun `fake serves the fake value`() {
        val koin = app(FirebaseMode.FAKE).koin
        assertThat(koin.get<RemoteValues>().string("showcase_remote_note", "default")).isEqualTo("from the fake")
        assertThat(koin.get<Analytics>()).isInstanceOf<ConsentGatedAnalytics>()
    }

    @Test fun `on applies no collection until consent resolves`() {
        val switch = RecordingSwitch()
        val port = FakeConsentPort(UmpResult(UmpStatus.OBTAINED, true, "1011"))
        val koin = app(FirebaseMode.ON, switch, port).koin
        assertThat(koin.get<RemoteValues>()).isInstanceOf<SourceRemoteValues>()
        assertThat(switch.applied).isEmpty()
        koin.get<Consent>().gatherOnLaunch()
        assertThat(switch.applied.last()).isEqualTo(FirebaseCollection(true, true, true))
    }

    @Test fun `a failed consent reaches the switch as all off`() {
        val switch = RecordingSwitch()
        val koin = app(FirebaseMode.ON, switch, FakeConsentPort(null)).koin
        koin.get<Consent>().gatherOnLaunch()
        assertThat(koin.get<Consent>().state.value).isEqualTo(ConsentState.Failed)
        assertThat(switch.applied).containsExactly(none)
    }

    @Test fun `a debug build without the switch turns collection off at start, before consent`() {
        // Flags a previous -analyticsDebug run persisted must not keep collecting.
        val switch = RecordingSwitch()
        app(FirebaseMode.ON, switch, FakeConsentPort(null), debugBuild = true, debugAnalytics = false)
        assertThat(switch.applied).containsExactly(none)
    }

    @Test fun `a debug build with the switch waits for consent like release`() {
        val switch = RecordingSwitch()
        app(FirebaseMode.ON, switch, FakeConsentPort(null), debugBuild = true, debugAnalytics = true)
        assertThat(switch.applied).isEmpty()
    }

    @Test fun `a withdrawal in the privacy options reaches the switch, even after a failing apply`() {
        // The first apply throws; the collector must survive it and apply the later change.
        val switch = object : CollectionSwitch {
            var calls = 0
            val applied = mutableListOf<FirebaseCollection>()
            override fun apply(collection: FirebaseCollection) {
                if (calls++ == 0) error("boom")
                applied += collection
            }
        }
        val port = FakeConsentPort(UmpResult(UmpStatus.OBTAINED, true, "1111111111"), optionsRequired = true)
        val koin = app(FirebaseMode.ON, port = port, override = switch).koin
        val consent = koin.get<Consent>()
        consent.gatherOnLaunch()
        assertThat(consent.state.value).isEqualTo(ConsentState.Resolved(true, true))
        port.next = UmpResult(UmpStatus.OBTAINED, true, "0")
        consent.showPrivacyOptions()
        assertThat(switch.applied).containsExactly(FirebaseCollection(analytics = true, analyticsStorage = false, crashes = true))
    }
}
