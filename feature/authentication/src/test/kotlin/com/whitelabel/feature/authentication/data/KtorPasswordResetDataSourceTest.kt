package com.whitelabel.feature.authentication.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
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
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

private object SignedIn : SessionTokenStore {
    override suspend fun current() = SessionTokens("access", "refresh")
    override suspend fun replace(expected: SessionTokens, new: SessionTokens) = false
    override suspend fun endSession(expected: SessionTokens) = Unit
}

private val json = headersOf(HttpHeaders.ContentType, "application/json")

class KtorPasswordResetDataSourceTest {

    private val requests = mutableListOf<HttpRequestData>()

    private fun source(status: HttpStatusCode, body: String) = KtorPasswordResetDataSource(
        HttpClientFactory.create(
            MockEngine { requests += it; respond(body, status, json) },
            NetworkConfig("https://api.test/api/v1/", tenant = null, userAgent = "test"),
            SignedIn,
        ),
    )

    private val ok = """{"success":true,"data":{},"meta":{"requestId":"r"}}"""

    @Test fun `request reset posts the trimmed email without a bearer`() = runTest {
        val result = source(HttpStatusCode.Accepted, ok).requestReset("  ada@example.com ")
        assertThat(result).isEqualTo(Result.Success(Unit))
        val request = requests.single()
        assertThat(request.url.encodedPath).isEqualTo("/api/v1/auth/forgot-password")
        assertThat((request.body as TextContent).text).isEqualTo("""{"email":"ada@example.com"}""")
        assertThat(request.headers[HttpHeaders.Authorization]).isNull()
    }

    @Test fun `reset posts token and untrimmed password`() = runTest {
        source(HttpStatusCode.OK, ok).reset("tok", " new password ")
        val request = requests.single()
        assertThat(request.url.encodedPath).isEqualTo("/api/v1/auth/reset-password")
        assertThat((request.body as TextContent).text).isEqualTo("""{"token":"tok","newPassword":" new password "}""")
        assertThat(request.headers[HttpHeaders.Authorization]).isNull()
    }

    @Test fun `expired reset token is reported`() = runTest {
        val body = """{"success":false,"error":{"code":"INVALID_RESET_TOKEN","message":"m"},"meta":{"requestId":"r"}}"""
        assertThat(source(HttpStatusCode.BadRequest, body).reset("tok", "0123456789"))
            .isEqualTo(Result.Error(DataError.Api(ApiErrorCode.INVALID_RESET_TOKEN, 400)))
    }
}
