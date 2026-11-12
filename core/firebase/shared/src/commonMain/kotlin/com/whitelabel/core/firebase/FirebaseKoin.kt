package com.whitelabel.core.firebase

import com.whitelabel.core.analytics.Analytics
import com.whitelabel.core.analytics.CrashReporter
import com.whitelabel.core.analytics.LoggingAnalytics
import com.whitelabel.core.analytics.LoggingCrashReporter
import com.whitelabel.core.consent.Consent
import com.whitelabel.core.domain.remote.RemoteValues
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

enum class FirebaseMode { OFF, ON, FAKE }

// The same qualifier as com.whitelabel.core.data.di.ApplicationScope; repeated to avoid depending on :core:data.
private val applicationScope = named("applicationScope")
private val firebase = named("firebase")

/**
 * OFF binds nothing (the defaults-only values stay). FAKE binds fake values and logging analytics behind the
 * consent gate. ON expects the platform to bind [RemoteConfigSource], [CollectionSwitch] and
 * [Analytics]/[CrashReporter] named "firebase"; it needs [consentModule] and the application scope too.
 */
fun firebaseModule(mode: FirebaseMode, debugBuild: Boolean, debugAnalytics: Boolean): Module = when (mode) {
    FirebaseMode.OFF -> module { }
    FirebaseMode.FAKE -> module {
        single<RemoteConfigSource> {
            FakeRemoteConfigSource(mapOf("showcase_remote_note" to "from the fake"))
        }
        single<RemoteValues> { SourceRemoteValues(get(), get(applicationScope)) }
        single<Analytics> { ConsentGatedAnalytics(LoggingAnalytics { println("Analytics: $it") }, get<Consent>().state) }
        single<CrashReporter> {
            ConsentGatedCrashReporter(
                LoggingCrashReporter { message, t -> println("$message: $t") },
                get<Consent>().state,
            )
        }
    }
    FirebaseMode.ON -> module {
        single { SourceRemoteValues(get(), get(applicationScope)) }
        single<RemoteValues>(createdAtStart = true) { get<SourceRemoteValues>().also { it.start() } }
        single<Analytics> { ConsentGatedAnalytics(get(firebase), get<Consent>().state) }
        single<CrashReporter> { ConsentGatedCrashReporter(get(firebase), get<Consent>().state) }
        single(named("firebaseCollectionApplier"), createdAtStart = true) {
            val switch = get<CollectionSwitch>()
            get<Consent>().state
                .onEach { switch.apply(collectionFor(it, debugBuild, debugAnalytics)) }
                .launchIn(get<CoroutineScope>(applicationScope))
        }
    }
}
