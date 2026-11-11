package com.whitelabel.core.testing

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isInstanceOf
import assertk.assertions.messageContains
import com.whitelabel.core.common.Result
import com.whitelabel.core.network.getResult
import com.whitelabel.core.network.postResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test

class ContractBackendTest {

    private val backend = ContractBackend()

    @Test fun `declared query parameters with valid values pass`() = runTest {
        val result = backend.client().getResult<kotlinx.serialization.json.JsonElement>("catalog", mapOf("q" to "jazz", "sort" to "title:asc", "page" to 2, "pageSize" to 10))
        assertThat(result).isInstanceOf(Result.Success::class)
    }

    @Test fun `an undeclared query parameter fails naming it`() = runTest {
        assertFailure { backend.client().getResult<JsonObject>("catalog", mapOf("query" to "jazz")) }.messageContains("query")
    }

    @Test fun `a query value outside its schema fails`() = runTest {
        assertFailure { backend.client().getResult<JsonObject>("catalog", mapOf("sort" to "title_asc")) }.messageContains("sort")
    }

    @Test fun `a secured operation without the bearer fails`() = runTest {
        assertFailure { backend.client(ContractBackend.SignedOut).getResult<JsonObject>("users/me") }.messageContains("bearer")
    }

    @Test fun `a public write carrying the bearer fails`() = runTest {
        val body = buildJsonObject { put("email", "a@b.co"); put("password", "x") }
        assertFailure { backend.client().postResult<JsonObject, JsonObject>("auth/login", body) }.messageContains("bearer")
    }
}
