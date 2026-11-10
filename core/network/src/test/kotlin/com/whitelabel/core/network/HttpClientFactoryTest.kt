package com.whitelabel.core.network

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.whitelabel.core.common.Result
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.header
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
private const val OK_BODY = """{"success":true,"data":{"status":"ok"},"meta":{"requestId":"r1"}}"""

class HttpClientFactoryTest {
    @Test fun `sends client id, accept and user agent headers`() = runTest {
        var captured: Map<String, String?> = emptyMap()
        val client = testClient { req ->
            captured = mapOf(
                CLIENT_ID_HEADER to req.headers[CLIENT_ID_HEADER],
                HttpHeaders.Accept to req.headers[HttpHeaders.Accept],
                HttpHeaders.UserAgent to req.headers[HttpHeaders.UserAgent],
            )
            respond(OK_BODY, HttpStatusCode.OK, jsonHeaders)
        }
        client.getResult<Health>("health")
        assertThat(captured[CLIENT_ID_HEADER]).isEqualTo("acme")
        assertThat(captured[HttpHeaders.Accept]).isEqualTo("application/json")
        assertThat(captured[HttpHeaders.UserAgent]).isEqualTo("test-agent/1.0")
    }

    @Test fun `non-ascii header values are sanitized to printable ascii and request is sent`() = runTest {
        var agent: String? = null
        var slug: String? = null
        val config = testConfig().copy(userAgent = "Caf\u00e9\u0001/1.0.0 (Android 16)", clientSlug = "caf\u00e9")
        val client = testClient(config) { req ->
            agent = req.headers[HttpHeaders.UserAgent]
            slug = req.headers[CLIENT_ID_HEADER]
            respond(OK_BODY, HttpStatusCode.OK, jsonHeaders)
        }
        val result = client.getResult<Health>("health")
        assertThat(result is Result.Success).isEqualTo(true)
        assertThat(agent).isEqualTo("Caf/1.0.0 (Android 16)")
        assertThat(slug).isEqualTo("caf")
    }

    @Test fun `relative path resolves under versioned base`() = runTest {
        var path = ""
        val client = testClient { req -> path = req.url.encodedPath; respond(OK_BODY, HttpStatusCode.OK, jsonHeaders) }
        client.getResult<Health>("health")
        assertThat(path).isEqualTo("/api/v1/health")
    }

    @Test fun `leading slash path still resolves under versioned base`() = runTest {
        var path = ""
        val client = testClient { req -> path = req.url.encodedPath; respond(OK_BODY, HttpStatusCode.OK, jsonHeaders) }
        client.getResult<Health>("/health")
        assertThat(path).isEqualTo("/api/v1/health")
    }

    @Test fun `base url without trailing slash still keeps version prefix`() = runTest {
        var path = ""
        val config = testConfig().copy(baseUrl = "https://api.test/api/v1")
        val client = testClient(config) { req -> path = req.url.encodedPath; respond(OK_BODY, HttpStatusCode.OK, jsonHeaders) }
        client.getResult<Health>("health")
        assertThat(path).isEqualTo("/api/v1/health")
    }

    @Test fun `null query values are omitted`() = runTest {
        var query = ""
        val client = testClient { req -> query = req.url.encodedQuery; respond(OK_BODY, HttpStatusCode.OK, jsonHeaders) }
        client.getResult<Health>("health", mapOf("a" to 1, "b" to null, "c" to "x"))
        assertThat(query).isEqualTo("a=1&c=x")
    }

    @Test fun `logging redacts authorization`() = runTest {
        val lines = mutableListOf<String>()
        val client = testClient(testConfig(logger = { lines += it })) { respond(OK_BODY, HttpStatusCode.OK, jsonHeaders) }
        client.get("health") { header(HttpHeaders.Authorization, "Bearer s3cret") }
        assertThat(lines.joinToString("\n")).doesNotContain("s3cret")
        assertThat(lines.joinToString("\n")).contains("Authorization")
    }

    @Test fun `no logger means no logging plugin output`() = runTest {
        val client = testClient { respond(OK_BODY, HttpStatusCode.OK, jsonHeaders) }
        assertThat(client.getResult<Health>("health")).isEqualTo(Result.Success(Health("ok")))
    }
}
