package com.whitelabel.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import kotlinx.serialization.Serializable

@Serializable
data class Health(val status: String)

const val TEST_BASE_URL = "https://api.test/api/v1/"
const val TEST_SLUG = "acme"

fun testConfig(logger: ((String) -> Unit)? = null) =
    NetworkConfig(baseUrl = TEST_BASE_URL, tenant = TEST_SLUG, userAgent = "test-agent/1.0", logger = logger)

fun testClient(
    config: NetworkConfig = testConfig(),
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
): HttpClient = HttpClientFactory.create(MockEngine(handler), config)
