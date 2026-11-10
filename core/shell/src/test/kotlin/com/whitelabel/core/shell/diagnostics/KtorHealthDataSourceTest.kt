package com.whitelabel.core.shell.diagnostics

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.whitelabel.core.common.ApiErrorCode
import com.whitelabel.core.common.DataError
import com.whitelabel.core.common.Result
import com.whitelabel.core.network.HttpClientFactory
import com.whitelabel.core.network.NetworkConfig
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class KtorHealthDataSourceTest {

    private var requestedUrl: String? = null

    private fun dataSource(status: HttpStatusCode, body: String): KtorHealthDataSource {
        val engine = MockEngine { request ->
            requestedUrl = request.url.toString()
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val config = NetworkConfig(
            baseUrl = "https://api.tenant.test/api/v1/",
            tenant = "tenant",
            userAgent = "test/1.0",
        )
        return KtorHealthDataSource(HttpClientFactory.create(engine, config))
    }

    @Test fun `up database maps to databaseUp true`() = runTest {
        val source = dataSource(
            HttpStatusCode.OK,
            """{"success":true,"data":{"status":"ok","db":"up"},"meta":{"requestId":"x"}}""",
        )
        assertThat(source.check()).isEqualTo(Result.Success(HealthStatus(status = "ok", databaseUp = true)))
        assertThat(requestedUrl).isEqualTo("https://api.tenant.test/api/v1/health")
    }

    @Test fun `down database maps to databaseUp false`() = runTest {
        val source = dataSource(
            HttpStatusCode.OK,
            """{"success":true,"data":{"status":"degraded","db":"down"},"meta":{"requestId":"x"}}""",
        )
        assertThat(source.check()).isEqualTo(Result.Success(HealthStatus(status = "degraded", databaseUp = false)))
    }

    @Test fun `server error surfaces DataError`() = runTest {
        val source = dataSource(
            HttpStatusCode.InternalServerError,
            """{"success":false,"error":{"code":"INTERNAL_ERROR","message":"boom"},"meta":{"requestId":"x"}}""",
        )
        assertThat(source.check())
            .isEqualTo(Result.Error(DataError.Api(code = ApiErrorCode.INTERNAL_ERROR, httpStatus = 500)))
    }
}
