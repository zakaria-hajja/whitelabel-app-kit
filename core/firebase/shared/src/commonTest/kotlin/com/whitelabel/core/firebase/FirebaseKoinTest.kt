package com.whitelabel.core.firebase

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.whitelabel.core.analytics.Analytics
import com.whitelabel.core.analytics.CrashReporter
import com.whitelabel.core.consent.Consent
import com.whitelabel.core.consent.ConsentPort
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

    private fun app(mode: FirebaseMode, switch: RecordingSwitch = RecordingSwitch(), port: ConsentPort? = null, override: CollectionSwitch? = null): KoinApplication =
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
                firebaseModule(mode, debugBuild = false, debugAnalytics = false),
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
        assertThat(switch.applied).containsExactly(FirebaseCollection(false, false, false))
        koin.get<Consent>().gatherOnLaunch()
        assertThat(switch.applied.last()).isEqualTo(FirebaseCollection(true, true, true))
    }

    @Test fun `a throwing switch does not stop later consent changes from applying`() {
        val applied = mutableListOf<FirebaseCollection>()
        var first = true
        val switch = object : CollectionSwitch {
            override fun apply(collection: FirebaseCollection) {
                if (first) { first = false; error("boom") }
                applied += collection
            }
        }
        val port = FakeConsentPort(UmpResult(UmpStatus.OBTAINED, true, "1011"))
        val koin = app(FirebaseMode.ON, RecordingSwitch(), port, switch).koin
        koin.get<Consent>().gatherOnLaunch()
        assertThat(applied).containsExactly(FirebaseCollection(true, true, true))
    }
}
