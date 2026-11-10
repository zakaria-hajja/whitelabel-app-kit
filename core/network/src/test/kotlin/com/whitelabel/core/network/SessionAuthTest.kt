package com.whitelabel.core.network

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.whitelabel.core.common.ApiErrorCode
import com.whitelabel.core.common.DataError
import com.whitelabel.core.common.Result
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Test

@Serializable
private data class Profile(val name: String)

private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
private val OLD = SessionTokens("old-access", "old-refresh")
private val NEW = SessionTokens("new-access", "new-refresh")
private const val NEW_PAIR =
    """{"success":true,"data":{"accessToken":"new-access","refreshToken":"new-refresh","tokenType":"Bearer","expiresIn":900},"meta":{"requestId":"r"}}"""
private const val OK_PROFILE = """{"success":true,"data":{"name":"Ada"},"meta":{"requestId":"r"}}"""

private fun MockRequestHandleScope.error(code: String, status: HttpStatusCode = HttpStatusCode.Unauthorized) =
    respond("""{"success":false,"error":{"code":"$code","message":"m"},"meta":{"requestId":"r"}}""", status, jsonHeaders)

private val HttpRequestData.bearer: String? get() = headers[HttpHeaders.Authorization]
private val HttpRequestData.isRefresh: Boolean get() = url.encodedPath.endsWith("/auth/refresh")

class SessionAuthTest {

    @Test fun `signed in request carries the bearer`() = runTest {
        var seen: String? = null
        val client = testClient(tokens = FakeSessionTokens(OLD)) { seen = it.bearer; respond(OK_PROFILE, HttpStatusCode.OK, jsonHeaders) }
        client.getResult<Profile>("users/me")
        assertThat(seen).isEqualTo("Bearer old-access")
    }

    @Test fun `signed out request has no authorization header`() = runTest {
        var seen: String? = "unset"
        val client = testClient(tokens = FakeSessionTokens(null)) { seen = it.bearer; respond(OK_PROFILE, HttpStatusCode.OK, jsonHeaders) }
        client.getResult<Profile>("users/me")
        assertThat(seen).isNull()
    }

    @Test fun `public request never carries the bearer`() = runTest {
        var seen: String? = "unset"
        val client = testClient(tokens = FakeSessionTokens(OLD)) { seen = it.bearer; respond(OK_PROFILE, HttpStatusCode.OK, jsonHeaders) }
        client.postPublicResult<Profile, Profile>("auth/login", Profile("x"))
        assertThat(seen).isNull()
    }

    @Test fun `expired token refreshes once stores the pair and retries`() = runTest {
        val store = FakeSessionTokens(OLD)
        var refreshBody: String? = null
        val client = testClient(tokens = store) { request ->
            when {
                request.isRefresh -> {
                    refreshBody = (request.body as TextContent).text
                    respond(NEW_PAIR, HttpStatusCode.OK, jsonHeaders)
                }
                request.bearer == "Bearer new-access" -> respond(OK_PROFILE, HttpStatusCode.OK, jsonHeaders)
                else -> error("TOKEN_EXPIRED")
            }
        }
        assertThat(client.getResult<Profile>("users/me")).isEqualTo(Result.Success(Profile("Ada")))
        assertThat(refreshBody).isEqualTo("""{"refreshToken":"old-refresh"}""")
        assertThat(store.tokens).isEqualTo(NEW)
        assertThat(store.replaced).isEqualTo(1)
    }

    @Test fun `concurrent expired calls share one refresh`() = runTest {
        val store = FakeSessionTokens(OLD)
        val refreshes = AtomicInteger()
        val expiredHits = AtomicInteger()
        val bothExpired = CompletableDeferred<Unit>()
        val client = testClient(tokens = store) { request ->
            when {
                request.isRefresh -> {
                    refreshes.incrementAndGet()
                    respond(NEW_PAIR, HttpStatusCode.OK, jsonHeaders)
                }
                request.bearer == "Bearer new-access" -> respond(OK_PROFILE, HttpStatusCode.OK, jsonHeaders)
                else -> {
                    // Hold both calls until each has been rejected with the old token.
                    if (expiredHits.incrementAndGet() == 2) bothExpired.complete(Unit)
                    bothExpired.await()
                    error("TOKEN_EXPIRED")
                }
            }
        }
        val first = async { client.getResult<Profile>("users/me") }
        val second = async { client.getResult<Profile>("catalog") }
        assertThat(first.await()).isEqualTo(Result.Success(Profile("Ada")))
        assertThat(second.await()).isEqualTo(Result.Success(Profile("Ada")))
        assertThat(refreshes.get()).isEqualTo(1)
    }

    @Test fun `rejected refresh ends the session`() = runTest {
        val store = FakeSessionTokens(OLD)
        val client = testClient(tokens = store) { request ->
            if (request.isRefresh) error("INVALID_REFRESH_TOKEN") else error("TOKEN_EXPIRED")
        }
        assertThat(client.getResult<Profile>("users/me"))
            .isEqualTo(Result.Error(DataError.Api(ApiErrorCode.TOKEN_EXPIRED, 401)))
        assertThat(store.ended).isEqualTo(1)
        assertThat(store.tokens).isNull()
    }

    @Test fun `refresh without network keeps the session`() = runTest {
        val store = FakeSessionTokens(OLD)
        var attempts = 0
        val client = testClient(tokens = store) { request ->
            if (request.isRefresh) { attempts++; throw IOException("offline") } else error("TOKEN_EXPIRED")
        }
        assertThat(client.getResult<Profile>("users/me")).isEqualTo(Result.Error(DataError.Network.NO_INTERNET))
        assertThat(attempts).isEqualTo(2)
        assertThat(store.ended).isEqualTo(0)
        assertThat(store.tokens).isEqualTo(OLD)
    }

    @Test fun `refresh server error keeps the session and reports it`() = runTest {
        val store = FakeSessionTokens(OLD)
        val client = testClient(tokens = store) { request ->
            if (request.isRefresh) error("INTERNAL_ERROR", HttpStatusCode.InternalServerError) else error("TOKEN_EXPIRED")
        }
        assertThat(client.getResult<Profile>("users/me"))
            .isEqualTo(Result.Error(DataError.Api(ApiErrorCode.INTERNAL_ERROR, 500)))
        assertThat(store.ended).isEqualTo(0)
        assertThat(store.tokens).isEqualTo(OLD)
    }

    @Test fun `unauthenticated with a bearer ends the session when the refresh is rejected`() = runTest {
        val store = FakeSessionTokens(OLD)
        val client = testClient(tokens = store) { request ->
            if (request.isRefresh) error("INVALID_REFRESH_TOKEN") else error("UNAUTHENTICATED")
        }
        assertThat(client.getResult<Profile>("users/me"))
            .isEqualTo(Result.Error(DataError.Api(ApiErrorCode.UNAUTHENTICATED, 401)))
        assertThat(store.ended).isEqualTo(1)
    }

    @Test fun `unauthenticated with a bearer recovers with one refresh`() = runTest {
        val store = FakeSessionTokens(OLD)
        val client = testClient(tokens = store) { request ->
            when {
                request.isRefresh -> respond(NEW_PAIR, HttpStatusCode.OK, jsonHeaders)
                request.bearer == "Bearer new-access" -> respond(OK_PROFILE, HttpStatusCode.OK, jsonHeaders)
                else -> error("UNAUTHENTICATED")
            }
        }
        assertThat(client.getResult<Profile>("users/me")).isEqualTo(Result.Success(Profile("Ada")))
        assertThat(store.ended).isEqualTo(0)
        assertThat(store.tokens).isEqualTo(NEW)
    }

    @Test fun `retry rejected with invalid credentials keeps the session`() = runTest {
        val store = FakeSessionTokens(OLD)
        val client = testClient(tokens = store) { request ->
            when {
                request.isRefresh -> respond(NEW_PAIR, HttpStatusCode.OK, jsonHeaders)
                request.bearer == "Bearer new-access" -> error("INVALID_CREDENTIALS")
                else -> error("TOKEN_EXPIRED")
            }
        }
        assertThat(client.patchResult<Profile, Profile>("users/me/password", Profile("x")))
            .isEqualTo(Result.Error(DataError.Api(ApiErrorCode.INVALID_CREDENTIALS, 401)))
        assertThat(store.ended).isEqualTo(0)
        assertThat(store.tokens).isEqualTo(NEW)
    }

    @Test fun `lost refresh response is retried once with the old token`() = runTest {
        val store = FakeSessionTokens(OLD)
        val refreshBodies = mutableListOf<String>()
        val client = testClient(tokens = store) { request ->
            when {
                request.isRefresh -> {
                    refreshBodies += (request.body as TextContent).text
                    if (refreshBodies.size == 1) throw IOException("connection reset") else respond(NEW_PAIR, HttpStatusCode.OK, jsonHeaders)
                }
                request.bearer == "Bearer new-access" -> respond(OK_PROFILE, HttpStatusCode.OK, jsonHeaders)
                else -> error("TOKEN_EXPIRED")
            }
        }
        assertThat(client.getResult<Profile>("users/me")).isEqualTo(Result.Success(Profile("Ada")))
        assertThat(refreshBodies).isEqualTo(List(2) { """{"refreshToken":"old-refresh"}""" })
        assertThat(store.tokens).isEqualTo(NEW)
    }

    @Test fun `refresh finishing after sign out does not sign back in`() = runTest {
        val store = FakeSessionTokens(OLD)
        val client = testClient(tokens = store) { request ->
            if (request.isRefresh) {
                store.tokens = null // the user signed out while the refresh was in flight
                respond(NEW_PAIR, HttpStatusCode.OK, jsonHeaders)
            } else {
                error("TOKEN_EXPIRED")
            }
        }
        assertThat(client.getResult<Profile>("users/me"))
            .isEqualTo(Result.Error(DataError.Api(ApiErrorCode.TOKEN_EXPIRED, 401)))
        assertThat(store.tokens).isNull()
    }

    @Test fun `stale unauthorized response does not end a newer session`() = runTest {
        val other = SessionTokens("other-access", "other-refresh")
        val store = FakeSessionTokens(OLD)
        var refreshed = false
        val client = testClient(tokens = store) { request ->
            when {
                request.isRefresh -> { refreshed = true; error("INVALID_REFRESH_TOKEN") }
                request.bearer == "Bearer other-access" -> respond(OK_PROFILE, HttpStatusCode.OK, jsonHeaders)
                else -> {
                    store.tokens = other // someone signed in again while this call was in flight
                    error("UNAUTHENTICATED")
                }
            }
        }
        assertThat(client.getResult<Profile>("users/me")).isEqualTo(Result.Success(Profile("Ada")))
        assertThat(refreshed).isEqualTo(false)
        assertThat(store.tokens).isEqualTo(other)
    }

    @Test fun `invalid credentials neither refreshes nor ends the session`() = runTest {
        val store = FakeSessionTokens(OLD)
        var refreshed = false
        val client = testClient(tokens = store) { request ->
            if (request.isRefresh) refreshed = true
            error("INVALID_CREDENTIALS")
        }
        assertThat(client.patchResult<Profile, Profile>("users/me/password", Profile("x")))
            .isEqualTo(Result.Error(DataError.Api(ApiErrorCode.INVALID_CREDENTIALS, 401)))
        assertThat(refreshed).isEqualTo(false)
        assertThat(store.ended).isEqualTo(0)
        assertThat(store.tokens).isEqualTo(OLD)
    }

    @Test fun `public request that is unauthorized is passed through`() = runTest {
        val store = FakeSessionTokens(OLD)
        var refreshed = false
        val client = testClient(tokens = store) { request ->
            if (request.isRefresh) refreshed = true
            error("INVALID_CREDENTIALS")
        }
        assertThat(client.postPublicResult<Profile, Profile>("auth/login", Profile("x")))
            .isEqualTo(Result.Error(DataError.Api(ApiErrorCode.INVALID_CREDENTIALS, 401)))
        assertThat(refreshed).isEqualTo(false)
        assertThat(store.tokens).isEqualTo(OLD)
    }

    @Test fun `a retry still unauthorized ends the session without a second refresh`() = runTest {
        val store = FakeSessionTokens(OLD)
        val refreshes = AtomicInteger()
        val client = testClient(tokens = store) { request ->
            if (request.isRefresh) {
                refreshes.incrementAndGet()
                respond(NEW_PAIR, HttpStatusCode.OK, jsonHeaders)
            } else {
                error("TOKEN_EXPIRED")
            }
        }
        assertThat(client.getResult<Profile>("users/me"))
            .isEqualTo(Result.Error(DataError.Api(ApiErrorCode.TOKEN_EXPIRED, 401)))
        assertThat(refreshes.get()).isEqualTo(1)
        assertThat(store.ended).isEqualTo(1)
    }
}
