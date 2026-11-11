package com.whitelabel.core.network

import org.koin.core.module.Module

/**
 * Bindings a module adds just by being on the classpath, e.g. `:core:mockapi` replacing the HTTP
 * engine in `-Pwhitelabel.mockApi=true` debug builds and end-to-end tests. The Android shell loads
 * every implementation with `java.util.ServiceLoader` (listed in
 * `META-INF/services/com.whitelabel.core.network.ModuleContributor`) when Koin starts.
 */
fun interface ModuleContributor {
    fun modules(): List<Module>
}
