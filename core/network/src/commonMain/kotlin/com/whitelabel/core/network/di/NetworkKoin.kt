package com.whitelabel.core.network.di

import com.whitelabel.core.network.HttpClientFactory
import io.ktor.client.HttpClient
import org.koin.dsl.module

/** The app's [HttpClient]. The shell provides the engine and `NetworkConfig`; `:core:data` the `SessionTokenStore`. */
val networkModule = module {
    single<HttpClient> { HttpClientFactory.create(get(), get(), get()) }
}
