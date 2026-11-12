package com.whitelabel.core.shell.brand

import com.whitelabel.core.domain.DebugGuestAccount
import org.koin.core.module.Module
import org.koin.dsl.module

/** Binds the account the sign-in screen's "Continue as guest (debug)" uses. Debug builds only ([buildTypeModules]). */
internal fun debugGuestModule(email: String, password: String): Module = module {
    single { DebugGuestAccount(email, password) }
}
