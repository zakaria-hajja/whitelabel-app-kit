package com.whitelabel.core.network

import com.whitelabel.core.common.ApiErrorCode
import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.util.AttributeKey
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

private val PublicRequest = AttributeKey<Unit>("PublicRequest")

/** Marks a request that never carries the session's bearer and never triggers a refresh (sign-in, refresh, …). */
fun HttpRequestBuilder.publicRequest() {
    attributes.put(PublicRequest, Unit)
}

@Serializable
private data class RefreshRequest(val refreshToken: String)

@Serializable
private data class TokenPairDto(val accessToken: String, val refreshToken: String)

/**
 * Bearer authentication that follows the backend's error codes rather than the bare 401 status
 * (`INVALID_CREDENTIALS` is a 401 too):
 * - `TOKEN_EXPIRED` → one refresh shared by every concurrent caller, then one retry;
 * - `UNAUTHENTICATED`, or the refresh rejected with a 401 → the session ends;
 * - anything else is returned untouched.
 *
 * A refresh that fails for another reason (no network, server error) keeps the session, and the
 * caller sees that failure.
 */
internal fun HttpClient.installSessionAuth(tokens: SessionTokenStore) {
    val refreshLock = Mutex()
    plugin(HttpSend).intercept { request ->
        if (request.attributes.contains(PublicRequest)) return@intercept execute(request)
        val sent = tokens.current() ?: return@intercept execute(request)
        request.bearer(sent.accessToken)
        val call = execute(request)
        if (call.response.status != HttpStatusCode.Unauthorized) return@intercept call
        when (call.errorCode()) {
            ApiErrorCode.TOKEN_EXPIRED -> {
                val outcome = refreshLock.withLock { renewedTokens(sent, tokens) }
                when (outcome) {
                    is Renewal.Renewed -> {
                        request.bearer(outcome.tokens.accessToken)
                        execute(request)
                    }
                    is Renewal.Failed -> outcome.call ?: call
                }
            }
            ApiErrorCode.UNAUTHENTICATED -> call.also { tokens.endSession() }
            else -> call
        }
    }
}

private sealed interface Renewal {
    data class Renewed(val tokens: SessionTokens) : Renewal

    /** [call] is the failed refresh to report, or `null` to report the original response. */
    data class Failed(val call: HttpClientCall?) : Renewal
}

/** Runs under the refresh lock: reuses a pair another caller already obtained, or refreshes. */
private suspend fun HttpClient.renewedTokens(sent: SessionTokens, tokens: SessionTokenStore): Renewal {
    val current = tokens.current() ?: return Renewal.Failed(null)
    if (current.accessToken != sent.accessToken) return Renewal.Renewed(current)
    val response = post(relativePath("auth/refresh")) {
        publicRequest()
        contentType(ContentType.Application.Json)
        setBody(RefreshRequest(current.refreshToken))
    }
    if (response.status == HttpStatusCode.Unauthorized) {
        tokens.endSession()
        return Renewal.Failed(null)
    }
    if (!response.status.isSuccess()) return Renewal.Failed(response.call)
    val pair = networkJson.decodeFromString(ApiEnvelope.serializer(TokenPairDto.serializer()), response.bodyAsText()).data
        ?: return Renewal.Failed(response.call)
    return SessionTokens(pair.accessToken, pair.refreshToken).also { tokens.replace(it) }.let(Renewal::Renewed)
}

private fun HttpRequestBuilder.bearer(accessToken: String) {
    headers[HttpHeaders.Authorization] = "Bearer $accessToken"
}

private suspend fun HttpClientCall.errorCode(): ApiErrorCode = try {
    ApiErrorCode.fromWire(networkJson.decodeFromString(ApiErrorEnvelope.serializer(), response.bodyAsText()).error?.code)
} catch (_: Exception) {
    ApiErrorCode.UNKNOWN
}

