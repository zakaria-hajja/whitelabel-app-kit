package com.whitelabel.core.shell

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameInstanceAs
import com.whitelabel.core.analytics.LoggingAnalytics
import com.whitelabel.core.analytics.LoggingCrashReporter
import com.whitelabel.core.consent.Consent
import com.whitelabel.core.consent.ConsentState
import com.whitelabel.core.consent.UmpResult
import com.whitelabel.core.consent.UmpStatus
import com.whitelabel.core.domain.remote.DefaultsOnlyRemoteValues
import com.whitelabel.core.domain.remote.RemoteValues
import com.whitelabel.core.firebase.FirebaseMode
import com.whitelabel.core.shell.brand.FirebaseWiring
import com.whitelabel.core.shell.brand.fakeConsentResult
import com.whitelabel.core.shell.brand.firebaseContributorPresent
import com.whitelabel.core.firebase.FirebaseServicesContributor
import com.whitelabel.core.shell.brand.firebaseModeFor
import com.whitelabel.core.shell.brand.foundationModules
import org.junit.jupiter.api.Test
import org.koin.dsl.koinApplication

class FirebaseWiringTest {

    private val withFirebase = appDefinition { add(entry("a", RouteA)); firebase() }

    // firebaseModeFor

    @Test fun `the fake resource gives the fake`() {
        assertThat(firebaseModeFor("fake", contributorPresent = false, fakeMarker = false, debugBuild = false)).isEqualTo(FirebaseMode.FAKE)
    }

    @Test fun `a debug fake marker gives the fake, a release one is ignored`() {
        assertThat(firebaseModeFor("off", contributorPresent = false, fakeMarker = true, debugBuild = true)).isEqualTo(FirebaseMode.FAKE)
        assertThat(firebaseModeFor("off", contributorPresent = false, fakeMarker = true, debugBuild = false)).isEqualTo(FirebaseMode.OFF)
    }

    @Test fun `on with the contributor is on`() {
        assertThat(firebaseModeFor("on", contributorPresent = true, fakeMarker = false, debugBuild = false)).isEqualTo(FirebaseMode.ON)
    }

    @Test fun `on without the contributor fails`() {
        assertFailure { firebaseModeFor("on", contributorPresent = false, fakeMarker = false, debugBuild = true) }
            .isInstanceOf<IllegalStateException>()
            .hasMessage("firebase is on but :core:firebase:google isn't linked")
    }

    @Test fun `anything else is off`() {
        assertThat(firebaseModeFor("off", contributorPresent = true, fakeMarker = false, debugBuild = true)).isEqualTo(FirebaseMode.OFF)
        assertThat(firebaseModeFor("", contributorPresent = false, fakeMarker = false, debugBuild = false)).isEqualTo(FirebaseMode.OFF)
    }

    // The contributor is found by type: R8 renames FirebaseContributor in release builds.

    @Test fun `a contributor marked as the firebase services is found whatever its class name`() {
        val renamed = object : FirebaseServicesContributor {
            override fun modules() = emptyList<org.koin.core.module.Module>()
        }
        assertThat(firebaseContributorPresent(listOf(renamed))).isEqualTo(true)
    }

    @Test fun `an unmarked contributor is not the firebase services`() {
        val mock = com.whitelabel.core.network.ModuleContributor { emptyList() }
        assertThat(firebaseContributorPresent(listOf(mock))).isEqualTo(false)
        assertThat(firebaseContributorPresent(emptyList())).isEqualTo(false)
    }

    // startup checks

    @Test fun `firebase() without the setting fails at startup`() {
        assertFailure { checkFirebaseDeclared(withFirebase, declared = false) }
            .isInstanceOf<IllegalStateException>()
            .hasMessage("firebase() needs firebase.enabled=true or debug in app.properties")
    }

    @Test fun `the setting without firebase() fails`() {
        assertFailure { checkFirebaseDeclared(appDefinition { add(entry("a", RouteA)) }, declared = true) }
            .isInstanceOf<IllegalStateException>()
            .hasMessage("firebase.enabled is set but the app definition doesn't call firebase()")
    }

    @Test fun `firebase() with the setting, or neither, passes`() {
        checkFirebaseDeclared(withFirebase, declared = true)
        checkFirebaseDeclared(appDefinition { add(entry("a", RouteA)) }, declared = false)
    }

    @Test fun `firebase() with analytics() fails`() {
        assertFailure {
            appDefinition { add(entry("a", RouteA)); firebase(); analytics(LoggingAnalytics { }) }
        }.isInstanceOf<IllegalStateException>().hasMessage("firebase() replaces analytics() and crashReporter(); use one")
        assertFailure {
            appDefinition { add(entry("a", RouteA)); crashReporter(LoggingCrashReporter { _, _ -> }); firebase() }
        }.isInstanceOf<IllegalStateException>().hasMessage("firebase() replaces analytics() and crashReporter(); use one")
    }

    // the graph

    @Test fun `mode off keeps the defaults-only remote values`() {
        val koin = koinApplication { modules(foundationModules(withFirebase, emptyList(), FirebaseWiring.OFF)) }.koin
        assertThat(koin.get<RemoteValues>()).isSameInstanceAs(DefaultsOnlyRemoteValues)
    }

    @Test fun `mode fake binds the fake source and its showcase value`() {
        val wiring = FirebaseWiring(FirebaseMode.FAKE, debugBuild = true, debugAnalytics = false, fakeConsent = fakeConsentResult("obtained"))
        val koin = koinApplication { modules(foundationModules(withFirebase, emptyList(), wiring)) }.koin
        assertThat(koin.get<RemoteValues>().string("showcase_remote_note", "default")).isEqualTo("from the fake")
    }

    @Test fun `mode fake replaces the real consent form with the marker's choice`() {
        val wiring = FirebaseWiring(FirebaseMode.FAKE, debugBuild = true, debugAnalytics = false, fakeConsent = fakeConsentResult("declined"))
        val koin = koinApplication { modules(foundationModules(withFirebase, emptyList(), wiring)) }.koin
        val consent = koin.get<Consent>()
        consent.gatherOnLaunch()
        assertThat(consent.state.value).isEqualTo(ConsentState.Resolved(canRequestAds = true, analyticsAllowed = false))
    }

    @Test fun `a failed fake gather leaves consent failed`() {
        val wiring = FirebaseWiring(FirebaseMode.FAKE, debugBuild = true, debugAnalytics = false, fakeConsent = fakeConsentResult("failed"))
        val koin = koinApplication { modules(foundationModules(withFirebase, emptyList(), wiring)) }.koin
        koin.get<Consent>().also { it.gatherOnLaunch() }
        assertThat(koin.get<Consent>().state.value).isEqualTo(ConsentState.Failed)
    }

    @Test fun `fake consent names map to ump results`() {
        assertThat(fakeConsentResult("notRequired")).isEqualTo(UmpResult(UmpStatus.NOT_REQUIRED, true, null))
        assertThat(fakeConsentResult("declined")).isEqualTo(UmpResult(UmpStatus.OBTAINED, true, "0"))
        assertThat(fakeConsentResult("failed")).isEqualTo(null)
        assertThat(fakeConsentResult("obtained")).isEqualTo(UmpResult(UmpStatus.OBTAINED, true, "1111111111"))
        assertThat(fakeConsentResult("anything else")).isEqualTo(fakeConsentResult("obtained"))
    }

    @Test fun `an app without firebase gets no consent binding`() {
        val koin = koinApplication {
            modules(foundationModules(appDefinition { add(entry("a", RouteA)) }, emptyList()))
        }.koin
        assertThat(koin.getOrNull<Consent>()).isEqualTo(null)
    }

    @Test fun `consentModule resolves against the real data module's application scope`() {
        val koin = koinApplication { modules(com.whitelabel.core.data.di.dataModule, com.whitelabel.core.consent.consentModule()) }.koin
        assertThat(koin.get<Consent>().state.value).isEqualTo(ConsentState.Unknown)
    }

    private fun stub(vararg modules: org.koin.core.module.Module) = object : com.whitelabel.core.network.ModuleContributor {
        override fun modules() = modules.toList()
    }

    @Test fun `a linked contributor can't replace the fake`() {
        val real = object : com.whitelabel.core.firebase.RemoteConfigSource {
            override fun raw(key: String) = "real"
            override fun fetchAndActivate(onDone: (Boolean) -> Unit) = onDone(false)
        }
        val contributor = stub(
            org.koin.dsl.module {
                single<com.whitelabel.core.firebase.RemoteConfigSource> { real }
                single<com.whitelabel.core.consent.ConsentPort> {
                    object : com.whitelabel.core.consent.ConsentPort {
                        override fun gather(onDone: (UmpResult?) -> Unit) = onDone(null)
                        override fun privacyOptionsRequired() = false
                        override fun showPrivacyOptions(onDone: (UmpResult?) -> Unit) = onDone(null)
                    }
                }
            },
        )
        val wiring = FirebaseWiring(FirebaseMode.FAKE, debugBuild = true, debugAnalytics = false, fakeConsent = fakeConsentResult("obtained"))
        val koin = koinApplication { modules(foundationModules(withFirebase, listOf(contributor), wiring)) }.koin
        assertThat(koin.get<RemoteValues>().string("showcase_remote_note", "default")).isEqualTo("from the fake")
        assertThat(koin.get<com.whitelabel.core.consent.ConsentPort>()).isInstanceOf<com.whitelabel.core.consent.FakeConsentPort>()
    }

    @Test fun `mode on gates the contributor's analytics and crashes behind consent`() {
        val firebase = org.koin.core.qualifier.named("firebase")
        val raw = object : com.whitelabel.core.analytics.Analytics {
            override fun track(event: com.whitelabel.core.analytics.AnalyticsEvent) = Unit
        }
        val contributor = stub(
            org.koin.dsl.module {
                single<com.whitelabel.core.firebase.RemoteConfigSource> {
                    object : com.whitelabel.core.firebase.RemoteConfigSource {
                        override fun raw(key: String): String? = null
                        override fun fetchAndActivate(onDone: (Boolean) -> Unit) = onDone(false)
                    }
                }
                single<com.whitelabel.core.analytics.Analytics>(firebase) { raw }
                single<com.whitelabel.core.analytics.CrashReporter>(firebase) { LoggingCrashReporter { _, _ -> } }
                single<com.whitelabel.core.firebase.CollectionSwitch> {
                    object : com.whitelabel.core.firebase.CollectionSwitch {
                        override fun apply(collection: com.whitelabel.core.firebase.FirebaseCollection) = Unit
                    }
                }
            },
        )
        val wiring = FirebaseWiring(FirebaseMode.ON, debugBuild = false, debugAnalytics = false)
        val koin = koinApplication { modules(foundationModules(withFirebase, listOf(contributor), wiring)) }.koin
        assertThat(koin.get<com.whitelabel.core.analytics.Analytics>())
            .isInstanceOf<com.whitelabel.core.firebase.ConsentGatedAnalytics>()
        assertThat(koin.get<com.whitelabel.core.analytics.CrashReporter>())
            .isInstanceOf<com.whitelabel.core.firebase.ConsentGatedCrashReporter>()
    }

    @Test fun `consent is gathered once, after the splash, only with firebase`() {
        assertThat(shouldGatherConsent(usesFirebase = true, splashKept = true, alreadyRequested = false)).isEqualTo(false)
        assertThat(shouldGatherConsent(usesFirebase = true, splashKept = false, alreadyRequested = false)).isEqualTo(true)
        assertThat(shouldGatherConsent(usesFirebase = true, splashKept = false, alreadyRequested = true)).isEqualTo(false)
        assertThat(shouldGatherConsent(usesFirebase = false, splashKept = false, alreadyRequested = false)).isEqualTo(false)
    }
}
