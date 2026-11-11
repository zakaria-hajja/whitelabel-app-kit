package com.whitelabel.feature.profile.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.whitelabel.core.common.ApiErrorCode
import com.whitelabel.core.common.DataError
import com.whitelabel.core.common.Result
import com.whitelabel.core.network.HttpClientFactory
import com.whitelabel.core.network.NetworkConfig
import com.whitelabel.core.network.SessionTokenStore
import com.whitelabel.core.network.SessionTokens
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

private class SignedIn : SessionTokenStore {
    var ended = 0
    override suspend fun current() = SessionTokens("access", "refresh")
    override suspend fun replace(expected: SessionTokens, new: SessionTokens) = false
    override suspend fun endSession(expected: SessionTokens) { ended++ }
}

private val json = headersOf(HttpHeaders.ContentType, "application/json")

class KtorPasswordDataSourceTest {

    private val requests = mutableListOf<HttpRequestData>()
    private val tokens = SignedIn()

    private fun source(status: HttpStatusCode, body: String) = KtorPasswordDataSource(
        HttpClientFactory.create(
            MockEngine { requests += it; respond(body, status, json) },
            NetworkConfig("https://api.test/api/v1/", tenant = null, userAgent = "test"),
            tokens,
        ),
    )

    @Test fun `change sends both passwords untrimmed with the bearer`() = runTest {
        val result = source(HttpStatusCode.OK, """{"success":true,"data":{},"meta":{"requestId":"r"}}""").change(" old ", "0123456789 ")
        assertThat(result).isEqualTo(Result.Success(Unit))
        val request = requests.single()
        assertThat(request.method).isEqualTo(HttpMethod.Patch)
        assertThat(request.url.encodedPath).isEqualTo("/api/v1/users/me/password")
        assertThat((request.body as TextContent).text).isEqualTo("""{"currentPassword":" old ","newPassword":"0123456789 "}""")
        assertThat(request.headers[HttpHeaders.Authorization]).isEqualTo("Bearer access")
    }

    @Test fun `wrong current password is reported and keeps the session`() = runTest {
        val body = """{"success":false,"error":{"code":"INVALID_CREDENTIALS","message":"m"},"meta":{"requestId":"r"}}"""
        assertThat(source(HttpStatusCode.Unauthorized, body).change("wrong", "0123456789"))
            .isEqualTo(Result.Error(DataError.Api(ApiErrorCode.INVALID_CREDENTIALS, 401)))
        assertThat(tokens.ended).isEqualTo(0)
        assertThat(requests.size).isEqualTo(1) // no refresh
    }
}
