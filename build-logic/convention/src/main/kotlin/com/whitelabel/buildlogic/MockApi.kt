package com.whitelabel.buildlogic

internal const val MOCK_API_PROPERTY = "whitelabel.mockApi"

/**
 * The dependency that swaps the HTTP engine for the in-memory backend: only for exactly `true`,
 * and only as `debugImplementation`, so a release build can never contain it.
 */
internal fun mockApiDependency(properties: Map<String, Any?>): Pair<String, String>? =
    if (properties[MOCK_API_PROPERTY] == "true") "debugImplementation" to ":core:mockapi" else null
