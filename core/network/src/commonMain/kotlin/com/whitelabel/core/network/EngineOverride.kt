package com.whitelabel.core.network

import io.ktor.client.engine.HttpClientEngine

/**
 * Replaces the app's HTTP engine when bound, e.g. by `:core:mockapi` in a `-Pwhitelabel.mockApi=true`
 * debug build or in end-to-end tests. Nothing binds it in a release build.
 */
fun interface EngineOverride {
    fun engine(): HttpClientEngine
}
