package com.whitelabel.core.network.di

import com.whitelabel.core.network.HttpClientFactory
import com.whitelabel.core.network.NetworkConfig
import com.whitelabel.core.network.SessionTokenStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import javax.inject.Singleton

/** The shell binds [HttpClientEngine] and [NetworkConfig]; `:core:data` binds [SessionTokenStore]. */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun httpClient(engine: HttpClientEngine, config: NetworkConfig, tokens: SessionTokenStore): HttpClient =
        HttpClientFactory.create(engine, config, tokens)
}
