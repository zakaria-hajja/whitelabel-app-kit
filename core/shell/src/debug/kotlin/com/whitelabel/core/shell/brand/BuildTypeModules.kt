package com.whitelabel.core.shell.brand

import android.content.Context
import com.whitelabel.core.shell.R
import org.koin.core.module.Module

/**
 * Debug builds' extra bindings: the guest account, from `foundation_debug_guest_*` (the seeded
 * alice by default; the app replaces them from `local.properties`). Release has none.
 */
internal fun buildTypeModules(context: Context): List<Module> = listOf(
    debugGuestModule(
        email = context.getString(R.string.foundation_debug_guest_email),
        password = context.getString(R.string.foundation_debug_guest_password),
    ),
)
