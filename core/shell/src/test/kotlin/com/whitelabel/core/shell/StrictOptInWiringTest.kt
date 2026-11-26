package com.whitelabel.core.shell

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import com.whitelabel.core.analytics.Analytics
import com.whitelabel.core.analytics.AnalyticsEvent
import com.whitelabel.core.analytics.CrashReporter
import com.whitelabel.core.analytics.LoggingCrashReporter
import com.whitelabel.core.analytics.UserProperty
import com.whitelabel.core.consent.Consent
import com.whitelabel.core.consent.ConsentPort
import com.whitelabel.core.consent.FakeConsentPort
import com.whitelabel.core.consent.UmpResult
import com.whitelabel.core.consent.UmpStatus
import com.whitelabel.core.consent.fakeConsentResult
import com.whitelabel.core.analytics.RecordingAnalytics
import assertk.assertions.isEmpty
import com.whitelabel.core.data.di.ApplicationScope
import com.whitelabel.core.firebase.CollectionSwitch
import com.whitelabel.core.firebase.FirebaseCollection
import com.whitelabel.core.firebase.FirebaseMode
import com.whitelabel.core.firebase.RemoteConfigSource
import com.whitelabel.core.network.ModuleContributor
import com.whitelabel.core.shell.brand.FirebaseWiring
import com.whitelabel.core.shell.brand.foundationModules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.jupiter.api.Test
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/** `firebase(analyticsOptIn = true)`: a user who declines analytics sends nothing. */
class StrictOptInWiringTest {
    private val log = mutableListOf<String>()

    // A decline: purposes "0" grants no storage, so analytics isn't allowed.
    private val contributor = object : ModuleContributor {
        override fun modules() = listOf(
            module {
                single<RemoteConfigSource> {
                    object : RemoteConfigSource {
                        override fun raw(key: String): String? = null
                        override fun fetchAndActivate(onDone: (Boolean) -> Unit) = onDone(false)
                    }
                }
                single<ConsentPort> { FakeConsentPort(UmpResult(UmpStatus.OBTAINED, true, "0")) }
                single<Analytics>(named("firebase")) {
                    object : Analytics {
                        override fun track(event: AnalyticsEvent) { log += "event ${event.name}" }
                        override fun setUserProperty(property: UserProperty) { log += "property ${property.name}" }
                    }
                }
                single<CrashReporter>(named("firebase")) { LoggingCrashReporter { _, _ -> } }
                single<CollectionSwitch> {
                    object : CollectionSwitch {
                        override fun apply(collection: FirebaseCollection) {
                            log += "apply analytics=${collection.analytics} storage=${collection.analyticsStorage}"
                        }
                    }
                }
            },
        )
    }

    private val scope = module {
        single<CoroutineScope>(ApplicationScope) { TestScope(UnconfinedTestDispatcher()) }
    }

    private fun declineAndSend(definition: AppDefinition, wiring: FirebaseWiring) {
        val koin = koinApplication { modules(foundationModules(definition, listOf(contributor), wiring) + scope) }.koin
        val analytics = koin.get<Analytics>()
        analytics.setUserProperty(UserProperty("bucket", "queued"))
        koin.get<Consent>().gatherOnLaunch()
        analytics.track(AnalyticsEvent("after"))
        analytics.setUserProperty(UserProperty("streak", "after"))
    }

    @Test fun `firebase() leaves strict opt-in off and firebase(analyticsOptIn = true) turns it on`() {
        assertThat(appDefinition { add(entry("a", RouteA)); firebase() }.analyticsOptIn).isEqualTo(false)
        assertThat(appDefinition { add(entry("a", RouteA)); firebase(analyticsOptIn = true) }.analyticsOptIn).isEqualTo(true)
    }

    @Test fun `the wiring defaults to strict opt-in off`() {
        assertThat(FirebaseWiring(FirebaseMode.ON, debugBuild = false, debugAnalytics = false).analyticsOptIn).isEqualTo(false)
        assertThat(FirebaseWiring.OFF.analyticsOptIn).isEqualTo(false)
    }

    @Test fun `mode on with strict opt-in turns analytics off after a decline and sends nothing`() {
        val definition = appDefinition { add(entry("a", RouteA)); firebase(analyticsOptIn = true) }
        declineAndSend(definition, FirebaseWiring(FirebaseMode.ON, debugBuild = false, debugAnalytics = false, analyticsOptIn = true))
        assertThat(log).containsExactly("apply analytics=false storage=false")
    }

    @Test fun `mode on without strict opt-in still counts a decliner without identifiers`() {
        val definition = appDefinition { add(entry("a", RouteA)); firebase() }
        declineAndSend(definition, FirebaseWiring(FirebaseMode.ON, debugBuild = false, debugAnalytics = false))
        assertThat(log).containsExactly("apply analytics=true storage=false", "event after", "property streak")
    }

    // Fake mode: the fake's sink is the "firebase" Analytics; a module after the foundation's replaces it.
    private fun fakeDeclineAndSend(definition: AppDefinition, analyticsOptIn: Boolean): RecordingAnalytics {
        val inner = RecordingAnalytics()
        val wiring = FirebaseWiring(
            FirebaseMode.FAKE, debugBuild = true, debugAnalytics = false,
            fakeConsent = fakeConsentResult("declined"), analyticsOptIn = analyticsOptIn,
        )
        val recorder = module { single<Analytics>(named("firebase")) { inner } }
        val koin = koinApplication { modules(foundationModules(definition, emptyList(), wiring) + scope + recorder) }.koin
        val analytics = koin.get<Analytics>()
        analytics.setUserProperty(UserProperty("bucket", "queued"))
        koin.get<Consent>().gatherOnLaunch()
        analytics.track(AnalyticsEvent("after"))
        analytics.setUserProperty(UserProperty("streak", "after"))
        return inner
    }

    @Test fun `mode fake with strict opt-in sends nothing after a decline`() {
        val inner = fakeDeclineAndSend(appDefinition { add(entry("a", RouteA)); firebase(analyticsOptIn = true) }, analyticsOptIn = true)
        assertThat(inner.events).isEmpty()
        assertThat(inner.userProperties).isEmpty()
    }

    @Test fun `mode fake without strict opt-in still counts a decliner`() {
        val inner = fakeDeclineAndSend(appDefinition { add(entry("a", RouteA)); firebase() }, analyticsOptIn = false)
        assertThat(inner.events.map { it.name }).containsExactly("after")
        assertThat(inner.userProperties).containsExactly(UserProperty("streak", "after"))
    }
}
