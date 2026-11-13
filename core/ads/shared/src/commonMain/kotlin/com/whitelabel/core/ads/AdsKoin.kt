package com.whitelabel.core.ads

import com.whitelabel.core.consent.Consent
import com.whitelabel.core.datastore.StorePaths
import kotlin.time.Clock
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

// The same qualifier as com.whitelabel.core.data.di.ApplicationScope; repeated to avoid depending on :core:data.
private val applicationScope = named("applicationScope")

/**
 * Binds [AdsController], [AdFree] and [LaunchCounter]. Needs [Consent], [StorePaths] and the application
 * scope. A [TrackingPort] is optional (none means [NoTracking]). Apps resolve [AdFree] for their rewards.
 */
fun adsModule(enabled: Boolean, units: Map<String, String>): Module = module {
    single { LaunchCounter(get<StorePaths>().path("ads-launches"), get(applicationScope)) }
    single { AdFree(get<StorePaths>().path("ads-free"), Clock.System, get(applicationScope)) }
    // Created at start so the launch is counted even if no banner is ever composed.
    single(createdAtStart = true) {
        AdsController(
            enabled, units, get<Consent>(), get(), get(),
            getOrNull<TrackingPort>() ?: NoTracking, Clock.System, get(applicationScope),
        )
    }
}
