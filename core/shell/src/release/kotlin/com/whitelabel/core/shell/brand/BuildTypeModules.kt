package com.whitelabel.core.shell.brand

import android.content.Context
import org.koin.core.module.Module

/** Release builds add nothing: no debug guest account, so no guest sign-in. */
@Suppress("UNUSED_PARAMETER")
internal fun buildTypeModules(context: Context): List<Module> = emptyList()
