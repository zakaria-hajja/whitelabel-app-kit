package com.whitelabel.core.network

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.whitelabel.core.common.ApiErrorCode
import com.whitelabel.core.common.DataError
import com.whitelabel.core.common.Result
import com.whitelabel.core.testing.ContractBackend
import com.whitelabel.core.testing.OpenApiSpec
import com.whitelabel.core.testing.exampleValue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Test

class ContractTest {

    private val backend = ContractBackend()

    @Test fun `post api v1 auth refresh matches the contract`() = runTest {
        val tokens = FakeSessionTokens(SessionTokens("old-access", "old-refresh"))
        backend.answerOnce("get", "/api/v1/users/me", 401)
        val result = backend.client(tokens).getResult<JsonObject>("users/me")
        assertThat(result).isInstanceOf(Result.Success::class)
        val expected = SessionTokens(
            OpenApiSpec.exampleValue("/api/v1/auth/refresh", "post", 200, "data.tokens.accessToken"),
            OpenApiSpec.exampleValue("/api/v1/auth/refresh", "post", 200, "data.tokens.refreshToken"),
        )
        assertThat(tokens.tokens).isEqualTo(expected)
        backend.assertRequestMatches("post", "/api/v1/auth/refresh")
    }

    @Test fun `every documented error maps to the code it names`() = runTest {
        val client = backend.client(ContractBackend.SignedOut)
        val checked = OpenApiSpec.operations().flatMap { (path, method) ->
            OpenApiSpec.documentedStatuses(path, method).filter { status ->
                // Error envelopes only: GET /health's 503 is documented as a success envelope (db: down).
                status >= 400 && OpenApiSpec.responseExampleOrNull(path, method, status)?.contains("\"success\":false") == true
            }.map { status ->
                backend.answer(method, path, status)
                val relative = path.removePrefix("/api/v1/").replace(Regex("\\{[^}]+}"), "x")
                val result = when (method) {
                    "get" -> client.getResult<JsonObject>(relative)
                    "post" -> client.postResult<JsonObject, JsonObject>(relative, JsonObject(emptyMap()))
                    "patch" -> client.patchResult<JsonObject, JsonObject>(relative, JsonObject(emptyMap()))
                    "delete" -> client.deleteResult<JsonObject>(relative)
                    else -> error("unexpected method $method")
                }
                val wire = OpenApiSpec.exampleValue(path, method, status, "error.code")
                val error = (result as Result.Error).error as DataError.Api
                assertThat(error.code.name, "$method $path $status").isEqualTo(wire)
                assertThat(error.code == ApiErrorCode.UNKNOWN, "$method $path $status").isEqualTo(false)
                assertThat(error.httpStatus).isEqualTo(status)
            }
        }
        assertThat(checked.isNotEmpty()).isEqualTo(true)
    }
}
