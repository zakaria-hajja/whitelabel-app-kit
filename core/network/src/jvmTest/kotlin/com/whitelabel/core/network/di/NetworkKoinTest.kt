package com.whitelabel.core.network.di

import com.whitelabel.core.network.NetworkConfig
import com.whitelabel.core.network.SessionTokenStore
import io.ktor.client.engine.HttpClientEngine
import org.junit.jupiter.api.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

@OptIn(KoinExperimentalAPI::class)
class NetworkKoinTest {

    @Test fun `network module resolves with the shell's engine and config and the data layer's tokens`() {
        networkModule.verify(extraTypes = listOf(HttpClientEngine::class, NetworkConfig::class, SessionTokenStore::class))
    }
}
