package com.whitelabel.core.mockapi

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.whitelabel.core.common.ApiErrorCode
import com.whitelabel.core.common.DataError
import com.whitelabel.core.common.Result
import com.whitelabel.core.network.SessionTokens
import com.whitelabel.core.network.getPageResult
import com.whitelabel.core.network.getResult
import com.whitelabel.core.network.postPublicResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import assertk.assertions.isGreaterThanOrEqualTo
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlin.test.Test

class FakeBackendTest {

    private val backend = FakeBackend()

    // The UI tests' slow-config case (-fakeConfigDelayMs): the config answer waits, in real time.
    @Test fun `a config delay holds the config answer that long`() = runTest {
        backend.configDelayMs = 300
        val start = TimeSource.Monotonic.markNow()
        backend.client().getResult<JsonObject>("app/config")
        val took = start.elapsedNow()
        assertThat(took).isGreaterThanOrEqualTo(300.milliseconds)
    }

    private suspend fun signIn(tokens: MemoryTokens, password: String = FakeData.PASSWORD): Result<JsonObject, DataError> {
        val body = buildJsonObject { put("email", "alice@shared.test"); put("password", password) }
        val result = backend.client().postPublicResult<JsonObject, JsonObject>("auth/login", body)
        if (result is Result.Success) {
            val pair = result.data["tokens"]!!.jsonObject
            tokens.tokens = SessionTokens(pair["accessToken"]!!.jsonPrimitive.content, pair["refreshToken"]!!.jsonPrimitive.content)
        }
        return result
    }

    @Test fun `seeded user signs in and the wrong password is refused`() = runTest {
        val tokens = MemoryTokens()
        assertThat(signIn(tokens, "nope")).isEqualTo(Result.Error(DataError.Api(ApiErrorCode.INVALID_CREDENTIALS, 401)))
        val ok = signIn(tokens)
        assertThat(ok).isInstanceOf(Result.Success::class)
        val me = backend.client(tokens).getResult<JsonObject>("users/me")
        assertThat((me as Result.Success).data["firstName"]!!.jsonPrimitive.content).isEqualTo("Alice")
    }

    @Test fun `refresh rotates and a reused refresh token is refused`() = runTest {
        val tokens = MemoryTokens()
        signIn(tokens)
        val first = tokens.tokens!!.refreshToken
        val body = buildJsonObject { put("refreshToken", first) }
        val rotated = backend.client().postPublicResult<JsonObject, JsonObject>("auth/refresh", body)
        assertThat(rotated).isInstanceOf(Result.Success::class)
        assertThat(backend.client().postPublicResult<JsonObject, JsonObject>("auth/refresh", body))
            .isEqualTo(Result.Error(DataError.Api(ApiErrorCode.INVALID_REFRESH_TOKEN, 401)))
    }

    @Test fun `expired access token refreshes through the real session plugin`() = runTest {
        val tokens = MemoryTokens()
        signIn(tokens)
        backend.expireAccessTokens()
        assertThat(backend.client(tokens).getResult<JsonObject>("users/me")).isInstanceOf(Result.Success::class)
        assertThat(backend.requests.count { it == "POST /api/v1/auth/refresh" }).isEqualTo(1)
    }

    @Test fun `revoked sessions end on the next call`() = runTest {
        val tokens = MemoryTokens()
        signIn(tokens)
        backend.revokeSessions()
        assertThat(backend.client(tokens).getResult<JsonObject>("users/me"))
            .isEqualTo(Result.Error(DataError.Api(ApiErrorCode.UNAUTHENTICATED, 401)))
        assertThat(tokens.ended).isEqualTo(1)
    }

    @Test fun `catalog pages searches filters and sorts like the backend`() = runTest {
        val tokens = MemoryTokens()
        signIn(tokens)
        val client = backend.client(tokens)
        val page3 = (client.getPageResult<JsonObject>("catalog", mapOf("page" to 3, "pageSize" to 10)) as Result.Success).data
        assertThat(page3.items.size).isEqualTo(5)
        assertThat(page3.totalPages).isEqualTo(3)
        val search = (client.getPageResult<JsonObject>("catalog", mapOf("q" to "item 1", "pageSize" to 100)) as Result.Success).data
        assertThat(search.items.size).isEqualTo(11)
        val music = (client.getPageResult<JsonObject>("catalog", mapOf("category" to "Music", "pageSize" to 100)) as Result.Success).data
        assertThat(music.items.map { it["title"]!!.jsonPrimitive.content }).containsExactly("Item 23", "Item 19", "Item 15", "Item 11", "Item 7", "Item 3")
        val byTitle = (client.getPageResult<JsonObject>("catalog", mapOf("sort" to "title:asc", "pageSize" to 3)) as Result.Success).data
        assertThat(byTitle.items.map { it["title"]!!.jsonPrimitive.content }).containsExactly("Item 1", "Item 10", "Item 11")
        val past = (client.getPageResult<JsonObject>("catalog", mapOf("page" to 9, "pageSize" to 10)) as Result.Success).data
        assertThat(past.items.size).isEqualTo(0)
    }

    @Test fun `catalog switched off answers feature disabled`() = runTest {
        val tokens = MemoryTokens()
        signIn(tokens)
        backend.features["catalog"] = false
        assertThat(backend.client(tokens).getPageResult<JsonObject>("catalog"))
            .isEqualTo(Result.Error(DataError.Api(ApiErrorCode.FEATURE_DISABLED, 404)))
    }

    @Test fun `offline throws an io error the client maps to no internet`() = runTest {
        backend.offline = true
        assertThat(backend.client().getResult<JsonObject>("health")).isEqualTo(Result.Error(DataError.Network.NO_INTERNET))
    }

    @Test fun `forgot password issues a reset token that resets once`() = runTest {
        val client = backend.client()
        client.postPublicResult<JsonObject, JsonObject>("auth/forgot-password", buildJsonObject { put("email", "alice@shared.test") })
        val token = checkNotNull(backend.lastResetToken)
        val reset = buildJsonObject { put("token", token); put("newPassword", "Another-pass-1") }
        assertThat(client.postPublicResult<JsonObject, JsonObject>("auth/reset-password", reset)).isInstanceOf(Result.Success::class)
        assertThat(client.postPublicResult<JsonObject, JsonObject>("auth/reset-password", reset))
            .isEqualTo(Result.Error(DataError.Api(ApiErrorCode.INVALID_RESET_TOKEN, 400)))
    }

    @Test fun `issueResetToken gives a token for a known account that resets the password`() = runTest {
        val token = backend.issueResetToken("alice@shared.test")!!
        val reset = buildJsonObject { put("token", token); put("newPassword", "Another-pass-1") }
        assertThat(backend.client().postPublicResult<JsonObject, JsonObject>("auth/reset-password", reset)).isInstanceOf(Result.Success::class)
    }

    @Test fun `issueResetToken gives nothing for an unknown account`() {
        assertThat(backend.issueResetToken("nobody@shared.test")).isEqualTo(null)
    }
}
