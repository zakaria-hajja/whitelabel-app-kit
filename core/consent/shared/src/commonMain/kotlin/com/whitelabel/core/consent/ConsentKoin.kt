package com.whitelabel.core.consent

import org.koin.core.qualifier.named
import org.koin.dsl.module

// The same qualifier as com.whitelabel.core.data.di.ApplicationScope (named("applicationScope")).
// It is repeated here so this small module doesn't depend on :core:data.
private val applicationScope = named("applicationScope")

/** Needs the app-wide CoroutineScope (from `dataModule`). A [ConsentPort] is optional. */
fun consentModule() = module {
    single { Consent(getOrNull<ConsentPort>(), get(applicationScope)) }
}
