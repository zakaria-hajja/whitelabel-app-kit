package com.whitelabel.core.mockapi

import com.whitelabel.core.network.HttpClientFactory
import com.whitelabel.core.network.NetworkConfig
import com.whitelabel.core.network.SessionTokenStore
import com.whitelabel.core.network.SessionTokens
import io.ktor.client.HttpClient

/** An in-memory session store, so the real session plugin runs against the fake. */
class MemoryTokens(var tokens: SessionTokens? = null) : SessionTokenStore {
    var ended = 0
    override suspend fun current() = tokens
    override suspend fun replace(expected: SessionTokens, new: SessionTokens): Boolean {
        if (tokens != expected) return false
        tokens = new
        return true
    }
    override suspend fun endSession(expected: SessionTokens) {
        if (tokens == expected) { tokens = null; ended++ }
    }
}

fun FakeBackend.client(tokens: SessionTokenStore = MemoryTokens()): HttpClient = HttpClientFactory.create(
    engine,
    NetworkConfig(baseUrl = "https://mock.invalid/api/v1/", tenant = null, userAgent = "fake-backend-test"),
    tokens,
)
