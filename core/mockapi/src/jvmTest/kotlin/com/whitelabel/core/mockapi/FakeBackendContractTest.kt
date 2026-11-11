package com.whitelabel.core.mockapi

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isGreaterThan
import com.whitelabel.core.network.SessionTokens
import com.whitelabel.core.network.deleteResult
import com.whitelabel.core.network.getPageResult
import com.whitelabel.core.network.getResult
import com.whitelabel.core.network.patchResult
import com.whitelabel.core.network.postPublicResult
import com.whitelabel.core.common.Result
import com.whitelabel.core.testing.OpenApiSpec
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.Test

/** A whole session against the fake: every response it gives must match the contract (Review Focus 1). */
class FakeBackendContractTest {

    @Test fun `every response of a full session matches the contract`() = runTest {
        val backend = FakeBackend()
        val tokens = MemoryTokens()
        val public = backend.client()
        val client = backend.client(tokens)
        fun json(vararg pairs: Pair<String, Any?>) = buildJsonObject {
            pairs.forEach { (k, v) -> when (v) { null -> put(k, JsonNull); is String -> put(k, v); is Boolean -> put(k, v); else -> error("unsupported") } }
        }

        public.getResult<JsonElement>("health")
        public.getResult<JsonElement>("app/config")
        public.postPublicResult<JsonObject, JsonElement>("auth/register", json("email" to "x@shared.test", "password" to "short"))
        public.postPublicResult<JsonObject, JsonElement>("auth/register", json("email" to "alice@shared.test", "password" to "0123456789"))
        public.postPublicResult<JsonObject, JsonElement>("auth/register", json("email" to "new@shared.test", "password" to "0123456789", "firstName" to "New"))
        public.postPublicResult<JsonObject, JsonElement>("auth/login", json("email" to "alice@shared.test", "password" to "wrong"))
        val login = public.postPublicResult<JsonObject, JsonObject>("auth/login", json("email" to "alice@shared.test", "password" to FakeData.PASSWORD))
        val pair = (login as Result.Success).data["tokens"]!!.jsonObject
        tokens.tokens = SessionTokens(pair["accessToken"]!!.jsonPrimitive.content, pair["refreshToken"]!!.jsonPrimitive.content)

        backend.client(MemoryTokens()).getResult<JsonElement>("users/me") // no bearer
        client.getResult<JsonElement>("users/me")
        client.patchResult<JsonObject, JsonElement>("users/me", json("firstName" to null, "lastName" to "Lovelace"))
        client.getResult<JsonElement>("settings")
        client.patchResult<JsonObject, JsonElement>("settings", buildJsonObject { put("theme", "DARK"); putJsonObject("notifications") { put("push", false) } })
        client.getPageResult<JsonElement>("catalog", mapOf("q" to "item", "category" to "Music", "sort" to "title:desc", "page" to 1, "pageSize" to 5))
        client.getPageResult<JsonElement>("catalog", mapOf("page" to 99))
        val first = (client.getPageResult<JsonObject>("catalog") as Result.Success).data.items.first()["id"]!!.jsonPrimitive.content
        client.getResult<JsonElement>("catalog/$first")
        client.getResult<JsonElement>("catalog/does-not-exist")
        backend.features["catalog"] = false
        client.getPageResult<JsonElement>("catalog")
        client.patchResult<JsonObject, JsonElement>("users/me/password", json("currentPassword" to "wrong", "newPassword" to "0123456789"))
        client.patchResult<JsonObject, JsonElement>("users/me/password", json("currentPassword" to FakeData.PASSWORD, "newPassword" to "short"))
        client.patchResult<JsonObject, JsonElement>("users/me/password", json("currentPassword" to FakeData.PASSWORD, "newPassword" to "Another-pass-1"))
        backend.expireAccessTokens()
        client.getResult<JsonElement>("users/me") // TOKEN_EXPIRED, refresh, retry
        client.getResult<JsonElement>("auth/me")
        public.postPublicResult<JsonObject, JsonElement>("auth/login", json("email" to "alice@shared.test", "password" to "Another-pass-1"))
        val sessions = (client.getResult<JsonObject>("auth/sessions") as Result.Success).data["sessions"]!!.jsonArray
        val other = sessions.map { it.jsonObject }.first { !it["current"]!!.jsonPrimitive.boolean }["id"]!!.jsonPrimitive.content
        client.deleteResult<JsonElement>("auth/sessions/$other")
        client.deleteResult<JsonElement>("auth/sessions/unknown")
        client.getPageResult<JsonElement>("catalog", mapOf("pageSize" to 101))
        client.getPageResult<JsonElement>("catalog", mapOf("page" to 0))
        client.getPageResult<JsonElement>("catalog", mapOf("sort" to "price:asc"))
        client.patchResult<JsonObject, JsonElement>("settings", json("theme" to "NEON"))
        client.patchResult<JsonObject, JsonElement>("settings", json("language" to "x"))
        client.patchResult<JsonObject, JsonElement>("users/me", json("firstName" to ""))
        public.postPublicResult<JsonObject, JsonElement>("auth/refresh", json("refreshToken" to "unknown"))
        public.postPublicResult<JsonObject, JsonElement>("auth/forgot-password", json("email" to "alice@shared.test"))
        public.postPublicResult<JsonObject, JsonElement>("auth/reset-password", json("token" to "unknown", "newPassword" to "0123456789"))
        public.postPublicResult<JsonObject, JsonElement>("auth/reset-password", json("token" to backend.lastResetToken!!, "newPassword" to "0123456789"))
        public.postPublicResult<JsonObject, JsonElement>("auth/logout", json("refreshToken" to tokens.tokens!!.refreshToken))

        val bob = MemoryTokens()
        val bobLogin = public.postPublicResult<JsonObject, JsonObject>("auth/login", json("email" to "bob@shared.test", "password" to FakeData.PASSWORD))
        val bobPair = (bobLogin as Result.Success).data["tokens"]!!.jsonObject
        bob.tokens = SessionTokens(bobPair["accessToken"]!!.jsonPrimitive.content, bobPair["refreshToken"]!!.jsonPrimitive.content)
        val bobClient = backend.client(bob)
        suspend fun deleteAccount(password: String) = bobClient.request("users/me") {
            method = HttpMethod.Delete
            contentType(ContentType.Application.Json)
            setBody(json("password" to password))
        }
        deleteAccount("")
        deleteAccount("wrong")
        deleteAccount(FakeData.PASSWORD)

        val problems = backend.exchanges.flatMap { exchange ->
            val template = OpenApiSpec.templateFor(exchange.path) ?: return@flatMap listOf("${exchange.path} is not in the contract")
            if (exchange.status !in OpenApiSpec.documentedStatuses(template, exchange.method)) {
                return@flatMap listOf("${exchange.method} $template answered ${exchange.status}, which the contract doesn't document")
            }
            OpenApiSpec.responseErrors(template, exchange.method, exchange.status, exchange.body).map { "${exchange.method} $template ${exchange.status}: $it" }
        }
        assertThat(problems).isEmpty()
        assertThat(backend.exchanges.size).isGreaterThan(25)
        // Every operation in the contract was exercised, so a route can't be added untested.
        val exercised = backend.exchanges.mapNotNull { e -> OpenApiSpec.templateFor(e.path)?.let { it to e.method } }.toSet()
        assertThat(OpenApiSpec.operations().filterNot { it in exercised }).isEmpty()
    }
}
