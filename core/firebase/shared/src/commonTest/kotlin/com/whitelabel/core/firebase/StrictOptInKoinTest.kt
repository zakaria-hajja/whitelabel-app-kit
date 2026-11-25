package com.whitelabel.core.firebase

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import com.whitelabel.core.analytics.Analytics
import com.whitelabel.core.analytics.AnalyticsEvent
import com.whitelabel.core.analytics.CrashReporter
import com.whitelabel.core.analytics.RecordingAnalytics
import com.whitelabel.core.analytics.UserProperty
import com.whitelabel.core.consent.Consent
import com.whitelabel.core.consent.ConsentPort
import com.whitelabel.core.consent.FakeConsentPort
import com.whitelabel.core.consent.UmpResult
import com.whitelabel.core.consent.UmpStatus
import com.whitelabel.core.consent.consentModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.koin.core.KoinApplication
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test

/** firebaseModule passes strict opt-in to both the collection applier and the analytics gate. */
@OptIn(ExperimentalCoroutinesApi::class)
class StrictOptInKoinTest {
    private class RecordingSwitch : CollectionSwitch {
        val applied = mutableListOf<FirebaseCollection>()
        override fun apply(collection: FirebaseCollection) { applied += collection }
    }

    private class NoopCrashes : CrashReporter {
        override fun record(throwable: Throwable) = Unit
    }

    // Purposes "0": purpose 1 (store/access) not granted, so analytics is declined.
    private val declined = UmpResult(UmpStatus.OBTAINED, true, "0")

    private fun app(
        mode: FirebaseMode,
        analyticsOptIn: Boolean,
        switch: RecordingSwitch = RecordingSwitch(),
        inner: Analytics = RecordingAnalytics(),
        port: ConsentPort = FakeConsentPort(declined),
    ): KoinApplication =
        koinApplication {
            modules(
                module {
                    single<CoroutineScope>(named("applicationScope")) { TestScope(UnconfinedTestDispatcher()) }
                    single<ConsentPort> { port }
                    single<CollectionSwitch> { switch }
                    single<RemoteConfigSource> { FakeRemoteConfigSource(mapOf("k" to "v")) }
                    single<Analytics>(named("firebase")) { inner }
                    single<CrashReporter>(named("firebase")) { NoopCrashes() }
                },
                consentModule(),
                firebaseModule(mode, debugBuild = false, debugAnalytics = false, analyticsOptIn = analyticsOptIn),
            )
        }.also { it.createEagerInstances() }

    @Test fun `on with strict opt-in applies analytics off after a decline and forwards nothing`() {
        val switch = RecordingSwitch()
        val inner = RecordingAnalytics()
        val koin = app(FirebaseMode.ON, analyticsOptIn = true, switch = switch, inner = inner).koin
        val analytics = koin.get<Analytics>()
        analytics.setUserProperty(UserProperty("bucket", "queued"))
        koin.get<Consent>().gatherOnLaunch()
        analytics.track(AnalyticsEvent("after"))
        analytics.setUserProperty(UserProperty("streak", "after"))

        assertThat(switch.applied).containsExactly(FirebaseCollection(analytics = false, analyticsStorage = false, crashes = true))
        assertThat(inner.events).isEmpty()
        assertThat(inner.userProperties).isEmpty()
    }

    @Test fun `on without strict opt-in keeps counting a decliner`() {
        val switch = RecordingSwitch()
        val inner = RecordingAnalytics()
        val koin = app(FirebaseMode.ON, analyticsOptIn = false, switch = switch, inner = inner).koin
        koin.get<Consent>().gatherOnLaunch()
        koin.get<Analytics>().track(AnalyticsEvent("after"))

        assertThat(switch.applied).containsExactly(FirebaseCollection(analytics = true, analyticsStorage = false, crashes = true))
        assertThat(inner.events.map { it.name }).containsExactly("after")
    }

    // FAKE binds its logging sink as the "firebase" Analytics; a later module replaces it with the recorder.
    private fun fake(analyticsOptIn: Boolean?): RecordingAnalytics {
        val inner = RecordingAnalytics()
        val koin = koinApplication {
            modules(
                module { single<CoroutineScope>(named("applicationScope")) { TestScope(UnconfinedTestDispatcher()) } },
                module { single<ConsentPort> { FakeConsentPort(declined) } },
                consentModule(),
                if (analyticsOptIn == null) {
                    firebaseModule(FirebaseMode.FAKE, debugBuild = false, debugAnalytics = false)
                } else {
                    firebaseModule(FirebaseMode.FAKE, debugBuild = false, debugAnalytics = false, analyticsOptIn = analyticsOptIn)
                },
                module { single<Analytics>(named("firebase")) { inner } },
            )
        }.koin
        val analytics = koin.get<Analytics>()
        analytics.setUserProperty(UserProperty("bucket", "queued"))
        koin.get<Consent>().gatherOnLaunch()
        analytics.track(AnalyticsEvent("after"))
        analytics.setUserProperty(UserProperty("streak", "after"))
        return inner
    }

    @Test fun `fake with strict opt-in forwards nothing after a decline`() {
        val inner = fake(analyticsOptIn = true)
        assertThat(inner.events).isEmpty()
        assertThat(inner.userProperties).isEmpty()
    }

    @Test fun `fake without strict opt-in keeps counting a decliner`() {
        val inner = fake(analyticsOptIn = false)
        assertThat(inner.events.map { it.name }).containsExactly("after")
        assertThat(inner.userProperties).containsExactly(UserProperty("streak", "after"))
    }

    @Test fun `the flag defaults to off`() {
        val inner = fake(analyticsOptIn = null)
        assertThat(inner.events.map { it.name }).containsExactly("after")
    }
}
