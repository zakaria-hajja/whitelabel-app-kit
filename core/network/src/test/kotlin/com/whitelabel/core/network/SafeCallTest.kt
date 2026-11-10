package com.whitelabel.core.network

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.whitelabel.core.common.ApiErrorCode
import com.whitelabel.core.common.DataError
import com.whitelabel.core.common.FieldError
import com.whitelabel.core.common.Result
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Test
import java.io.IOException

@Serializable
private data class Echo(val name: String)

private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

class SafeCallTest {
    @Test fun `success envelope unwraps data and ignores unknown fields`() = runTest {
        val client = testClient {
            respond(
                """{"success":true,"data":{"status":"ok","unknown":1},"meta":{"requestId":"r1","future":true},"other":1}""",
                HttpStatusCode.OK, jsonHeaders,
            )
        }
        assertThat(client.getResult<Health>("health")).isEqualTo(Result.Success(Health("ok")))
    }

    @Test fun `post serializes body as json`() = runTest {
        var contentType: String? = null
        var body = ""
        var method = ""
        val client = testClient { req ->
            method = req.method.value
            contentType = req.body.contentType?.withoutParameters()?.toString()
            body = (req.body as TextContent).text
            respond("""{"success":true,"data":{"name":"bob"},"meta":{"requestId":"r1"}}""", HttpStatusCode.OK, jsonHeaders)
        }
        val result = client.postResult<Echo, Echo>("echo", Echo("bob"))
        assertThat(result).isEqualTo(Result.Success(Echo("bob")))
        assertThat(method).isEqualTo("POST")
        assertThat(contentType).isEqualTo("application/json")
        assertThat(body).isEqualTo("""{"name":"bob"}""")
    }

    @Test fun `patch and delete use the right methods`() = runTest {
        val methods = mutableListOf<String>()
        val client = testClient { req ->
            methods += req.method.value
            respond("""{"success":true,"data":{"name":"x"},"meta":{"requestId":"r1"}}""", HttpStatusCode.OK, jsonHeaders)
        }
        client.patchResult<Echo, Echo>("echo", Echo("x"))
        client.deleteResult<Echo>("echo")
        assertThat(methods).isEqualTo(listOf("PATCH", "DELETE"))
    }

    @Test fun `error envelope maps code, status and field errors`() = runTest {
        val client = testClient {
            respond(
                """{"success":false,"error":{"code":"VALIDATION_ERROR","message":"Bad","details":[{"path":"email","message":"Invalid email"}]},"meta":{"requestId":"r1"}}""",
                HttpStatusCode.BadRequest, jsonHeaders,
            )
        }
        assertThat(client.getResult<Health>("health")).isEqualTo(
            Result.Error(DataError.Api(ApiErrorCode.VALIDATION_ERROR, 400, listOf(FieldError("email", "Invalid email")))),
        )
    }

    @Test fun `unknown error code maps to UNKNOWN keeping status`() = runTest {
        val client = testClient {
            respond(
                """{"success":false,"error":{"code":"SOMETHING_NEW","message":"?"},"meta":{"requestId":"r1"}}""",
                HttpStatusCode.Conflict, jsonHeaders,
            )
        }
        assertThat(client.getResult<Health>("health")).isEqualTo(Result.Error(DataError.Api(ApiErrorCode.UNKNOWN, 409)))
    }

    @Test fun `html 502 body maps to INTERNAL_ERROR`() = runTest {
        val client = testClient {
            respond("<html>Bad gateway</html>", HttpStatusCode.BadGateway, headersOf(HttpHeaders.ContentType, "text/html"))
        }
        assertThat(client.getResult<Health>("health")).isEqualTo(Result.Error(DataError.Api(ApiErrorCode.INTERNAL_ERROR, 502)))
    }

    @Test fun `empty 401 body maps to UNAUTHENTICATED`() = runTest {
        val client = testClient { respondError(HttpStatusCode.Unauthorized) }
        assertThat(client.getResult<Health>("health")).isEqualTo(Result.Error(DataError.Api(ApiErrorCode.UNAUTHENTICATED, 401)))
    }

    @Test fun `rate limited parses retry-after seconds`() = runTest {
        val client = testClient {
            respond(
                """{"success":false,"error":{"code":"RATE_LIMITED","message":"slow"},"meta":{"requestId":"r1"}}""",
                HttpStatusCode.TooManyRequests,
                headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.RetryAfter to listOf("30")),
            )
        }
        assertThat(client.getResult<Health>("health")).isEqualTo(
            Result.Error(DataError.Api(ApiErrorCode.RATE_LIMITED, 429, retryAfterSeconds = 30L)),
        )
    }

    @Test fun `malformed success body maps to SERIALIZATION`() = runTest {
        val client = testClient {
            respond("""{"success":true,"data":{"status":{"nested":1}},"meta":{"requestId":"r1"}}""", HttpStatusCode.OK, jsonHeaders)
        }
        assertThat(client.getResult<Health>("health")).isEqualTo(Result.Error(DataError.Network.SERIALIZATION))
    }

    @Test fun `io failure maps to NO_INTERNET`() = runTest {
        val client = testClient { throw IOException("down") }
        assertThat(client.getResult<Health>("health")).isEqualTo(Result.Error(DataError.Network.NO_INTERNET))
    }

    @Test fun `unresolved address maps to NO_INTERNET`() = runTest {
        val client = testClient { throw java.nio.channels.UnresolvedAddressException() }
        assertThat(client.getResult<Health>("health")).isEqualTo(Result.Error(DataError.Network.NO_INTERNET))
    }

    @Test fun `timeout maps to REQUEST_TIMEOUT`() = runTest {
        val client = testClient { throw SocketTimeoutException("slow") }
        assertThat(client.getResult<Health>("health")).isEqualTo(Result.Error(DataError.Network.REQUEST_TIMEOUT))
    }

    @Test fun `unexpected exception maps to UNKNOWN`() = runTest {
        val client = testClient { throw IllegalStateException("boom") }
        assertThat(client.getResult<Health>("health")).isEqualTo(Result.Error(DataError.Network.UNKNOWN))
    }

    @Test fun `cancellation propagates`() = runTest {
        val started = CompletableDeferred<Unit>()
        val client = testClient { started.complete(Unit); awaitCancellation() }
        var produced: Result<Health, DataError>? = null
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            produced = client.getResult<Health>("health")
        }
        started.await()
        job.cancel()
        job.join()
        assertThat(job.isCancelled).isTrue()
        assertThat(produced).isEqualTo(null)
    }
}
