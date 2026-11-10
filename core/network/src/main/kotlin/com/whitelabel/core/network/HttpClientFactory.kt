package com.whitelabel.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json

private const val CONNECT_TIMEOUT_MS = 15_000L
private const val REQUEST_TIMEOUT_MS = 30_000L

// OkHttp rejects header values outside printable ASCII, which would surface as a silent network error.
private fun String.asHeaderValue(): String = filter { it.code in 0x20..0x7E }

object HttpClientFactory {
    /** [tokens] supplies the session's bearer and receives refreshed pairs; see [installSessionAuth]. */
    fun create(engine: HttpClientEngine, config: NetworkConfig, tokens: SessionTokenStore): HttpClient = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) { json(networkJson) }
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
        }
        defaultRequest {
            // The trailing slash makes relative paths resolve under the versioned base.
            url(config.baseUrl.trimEnd('/') + "/")
            config.tenant?.let { header(CLIENT_ID_HEADER, it.asHeaderValue()) }
            header(HttpHeaders.UserAgent, config.userAgent.asHeaderValue())
            accept(ContentType.Application.Json)
        }
        config.logger?.let { sink ->
            install(Logging) {
                level = LogLevel.HEADERS
                logger = object : Logger {
                    override fun log(message: String) = sink(message)
                }
                sanitizeHeader { it == HttpHeaders.Authorization }
            }
        }
    }.also { it.installSessionAuth(tokens) }
}
