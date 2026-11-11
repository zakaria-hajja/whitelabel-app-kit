package com.whitelabel.core.testing

import com.whitelabel.core.network.HttpClientFactory
import com.whitelabel.core.network.NetworkConfig
import com.whitelabel.core.network.SessionTokenStore
import com.whitelabel.core.network.SessionTokens
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf

/** A request the app sent, with the spec path it matched. */
data class RecordedRequest(val method: String, val specPath: String, val body: String?)

/**
 * A backend that answers every request with the spec's own example for that operation, and records
 * what the app sent so its body can be checked against the request schema. Answers are 2xx unless
 * [answer] picks another documented status for an operation.
 */
class ContractBackend {
    val requests = mutableListOf<RecordedRequest>()
    private val statuses = mutableMapOf<Pair<String, String>, Int>()
    private val onceStatuses = mutableMapOf<Pair<String, String>, ArrayDeque<Int>>()

    /** Answers [method] [specPath] with the example of [status] from now on. */
    fun answer(method: String, specPath: String, status: Int) {
        statuses[method.lowercase() to specPath] = status
    }

    /** Answers the next [method] [specPath] with the example of [status], then as before. */
    fun answerOnce(method: String, specPath: String, status: Int) {
        onceStatuses.getOrPut(method.lowercase() to specPath) { ArrayDeque() }.addLast(status)
    }

    fun client(tokens: SessionTokenStore = SignedIn): HttpClient = HttpClientFactory.create(
        MockEngine { request -> handle(request) },
        NetworkConfig(baseUrl = "https://api.test/api/v1/", tenant = null, userAgent = "contract-test"),
        tokens,
    )

    /** The request body the app sent to [specPath], checked against the spec; fails naming every problem. */
    fun assertRequestMatches(method: String, specPath: String) {
        val sent = requests.lastOrNull { it.method == method.lowercase() && it.specPath == specPath }
            ?: throw AssertionError("No $method $specPath was sent; sent: ${requests.map { "${it.method} ${it.specPath}" }}")
        val body = sent.body ?: throw AssertionError("$method $specPath was sent without a body")
        val errors = OpenApiSpec.requestErrors(specPath, method, body)
        if (errors.isNotEmpty()) throw AssertionError("$method $specPath body $body breaks the contract: $errors")
    }

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.handle(request: HttpRequestData) =
        run {
            val method = request.method.value.lowercase()
            val specPath = OpenApiSpec.templateFor(request.url.encodedPath)
                ?: throw AssertionError("${request.url.encodedPath} is not in the contract")
            requests += RecordedRequest(method, specPath, (request.body as? TextContent)?.text ?: (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString())
            val status = onceStatuses[method to specPath]?.removeFirstOrNull()
                ?: statuses[method to specPath]
                ?: OpenApiSpec.documentedStatuses(specPath, method).first { it in 200..299 }
            respond(
                OpenApiSpec.responseExample(specPath, method, status),
                HttpStatusCode.fromValue(status),
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

    /** Signed out: no bearer, so no refresh logic runs. */
    object SignedOut : SessionTokenStore {
        override suspend fun current(): SessionTokens? = null
        override suspend fun replace(expected: SessionTokens, new: SessionTokens) = false
        override suspend fun endSession(expected: SessionTokens) = Unit
    }

    /** Signed in with fixed tokens, so authenticated calls carry a bearer. */
    object SignedIn : SessionTokenStore {
        override suspend fun current() = SessionTokens("contract-access", "contract-refresh")
        override suspend fun replace(expected: SessionTokens, new: SessionTokens) = true
        override suspend fun endSession(expected: SessionTokens) = Unit
    }
}
