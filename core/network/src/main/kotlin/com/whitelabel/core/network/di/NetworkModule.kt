package com.whitelabel.core.network.di

import com.whitelabel.core.network.HttpClientFactory
import com.whitelabel.core.network.NetworkConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import javax.inject.Singleton

/** `:app` binds [HttpClientEngine] and [NetworkConfig]. */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun httpClient(engine: HttpClientEngine, config: NetworkConfig): HttpClient =
        HttpClientFactory.create(engine, config)
}
