package com.whitelabel.core.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import kotlinx.serialization.Serializable

@Serializable data object RouteA
@Serializable data object RouteB
@Serializable data object RouteC

fun tab(route: Any) = Tab(route = route, label = 0, icon = Icons.Default.Home)

fun entry(
    id: String,
    route: Any? = null,
    flagKey: String? = null,
    requiresSignIn: Boolean = false,
    gate: Gate? = null,
    requiresBackend: Boolean = false,
    modules: List<org.koin.core.module.Module> = emptyList(),
) = FeatureEntry(
    id = id,
    flagKey = flagKey,
    tab = route?.let(::tab),
    requiresSignIn = requiresSignIn,
    gate = gate,
    requiresBackend = requiresBackend,
    modules = modules,
    routes = {},
)
