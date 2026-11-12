package com.whitelabel.core.consent

import org.koin.dsl.module

/** Binds the app's [Consent]. A [ConsentPort] is optional: without one, consent resolves to everything denied. */
fun consentModule() = module {
    single { Consent(getOrNull<ConsentPort>()) }
}
