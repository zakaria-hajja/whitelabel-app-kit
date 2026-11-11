package com.whitelabel.core.network

import com.whitelabel.core.common.ApiErrorCode
import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.util.AttributeKey
import java.io.IOException
import kotlinx.coroutines.CancellationException
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

/** `POST auth/refresh` answers with the pair under `data.tokens` (`TokensEnvelope`). */
@Serializable
private data class RefreshResponse(val tokens: TokenPairDto)

/**
 * Bearer authentication following the backend's "Android client guidance" (docs/api.md), which
 * branches on error codes rather than the bare 401 status (`INVALID_CREDENTIALS` is a 401 too):
 * - `TOKEN_EXPIRED` or `UNAUTHENTICATED` on a request that carried the bearer → one refresh shared
 *   by every concurrent caller, then one retry. A retry still `TOKEN_EXPIRED`/`UNAUTHENTICATED`
 *   ends the session.
 * - a refresh rejected with a 401 ends the session.
 * - a refresh that fails ambiguously (no response: timeout, dropped connection) is retried once at
 *   once with the same refresh token, as the backend requires for lost responses; if that fails
 *   too the session is kept and the caller sees the failure.
 * - anything else is returned untouched.
 *
 * Every write is conditional on the pair the request used, so a refresh or a stale response that
 * lands after sign-out (or after another sign-in) never changes the newer session.
 */
internal fun HttpClient.installSessionAuth(tokens: SessionTokenStore) {
    val refreshLock = Mutex()
    plugin(HttpSend).intercept { request ->
        if (request.attributes.contains(PublicRequest)) return@intercept execute(request)
        val sent = tokens.current() ?: return@intercept execute(request)
        request.bearer(sent.accessToken)
        val call = execute(request)
        if (!call.isSessionRejection()) return@intercept call
        when (val outcome = refreshLock.withLock { renewedTokens(sent, tokens) }) {
            is Renewal.Failed -> outcome.call ?: call
            is Renewal.Renewed -> {
                request.bearer(outcome.tokens.accessToken)
                execute(request).also { retry ->
                    if (retry.isSessionRejection()) tokens.endSession(outcome.tokens)
                }
            }
        }
    }
}

private sealed interface Renewal {
    data class Renewed(val tokens: SessionTokens) : Renewal

    /** [call] is the failed refresh to report, or `null` to report the original response. */
    data class Failed(val call: HttpClientCall?) : Renewal
}

/** A 401 that means the access token no longer works (as opposed to, say, a wrong password). */
private suspend fun HttpClientCall.isSessionRejection(): Boolean =
    response.status == HttpStatusCode.Unauthorized &&
        errorCode().let { it == ApiErrorCode.TOKEN_EXPIRED || it == ApiErrorCode.UNAUTHENTICATED }

/** Runs under the refresh lock: reuses a pair another caller already obtained, or refreshes. */
private suspend fun HttpClient.renewedTokens(sent: SessionTokens, tokens: SessionTokenStore): Renewal {
    val current = tokens.current() ?: return Renewal.Failed(null)
    if (current.accessToken != sent.accessToken) return Renewal.Renewed(current)
    val response = try {
        refresh(current)
    } catch (e: CancellationException) {
        throw e
    } catch (_: IOException) {
        // No response: the server may have rotated the pair. Retry once now with the same token.
        refresh(current)
    }
    if (response.status == HttpStatusCode.Unauthorized) {
        tokens.endSession(current)
        return Renewal.Failed(null)
    }
    if (!response.status.isSuccess()) return Renewal.Failed(response.call)
    val pair = networkJson.decodeFromString(ApiEnvelope.serializer(RefreshResponse.serializer()), response.bodyAsText()).data?.tokens
        ?: return Renewal.Failed(response.call)
    val renewed = SessionTokens(pair.accessToken, pair.refreshToken)
    return if (tokens.replace(current, renewed)) Renewal.Renewed(renewed) else Renewal.Failed(null)
}

private suspend fun HttpClient.refresh(current: SessionTokens): HttpResponse = post(relativePath("auth/refresh")) {
    publicRequest()
    contentType(ContentType.Application.Json)
    setBody(RefreshRequest(current.refreshToken))
}

private fun HttpRequestBuilder.bearer(accessToken: String) {
    headers[HttpHeaders.Authorization] = "Bearer $accessToken"
}

private suspend fun HttpClientCall.errorCode(): ApiErrorCode = try {
    ApiErrorCode.fromWire(networkJson.decodeFromString(ApiErrorEnvelope.serializer(), response.bodyAsText()).error?.code)
} catch (_: Exception) {
    ApiErrorCode.UNKNOWN
}
