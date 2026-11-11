package com.whitelabel.core.shell.model

fun feature(
    id: String,
    tab: String? = null,
    flagKey: String? = null,
    requiresSignIn: Boolean = false,
    isGate: Boolean = false,
    requiresBackend: Boolean = false,
) = FeatureSpec(id = id, flagKey = flagKey, tabRoute = tab, requiresSignIn = requiresSignIn, isGate = isGate, requiresBackend = requiresBackend)
