package com.whitelabel.core.mockapi

import assertk.assertThat
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import com.whitelabel.core.common.Result
import com.whitelabel.core.network.postPublicResult
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** E2E tests and the app hit the fake from several threads at once; its state must stay consistent. */
class FakeBackendConcurrencyTest {

    private val backend = FakeBackend()

    @Test fun `parallel sign ins and refreshes keep every session`() = runTest {
        val outcomes = withContext(Dispatchers.Default) {
            coroutineScope {
                (1..50).map {
                    async {
                        val login = buildJsonObject { put("email", "alice@shared.test"); put("password", FakeData.PASSWORD) }
                        val signedIn = backend.client().postPublicResult<JsonObject, JsonObject>("auth/login", login)
                        val refresh = (signedIn as Result.Success).data["tokens"]!!.jsonObject["refreshToken"]!!.jsonPrimitive.content
                        val refreshed = backend.client().postPublicResult<JsonObject, JsonObject>("auth/refresh", buildJsonObject { put("refreshToken", refresh) })
                        if (refreshed is Result.Success) refreshed.data["tokens"]!!.jsonObject["refreshToken"]!!.jsonPrimitive.content else "failed: $refreshed"
                    }
                }.awaitAll()
            }
        }
        assertThat(outcomes.filter { it.startsWith("failed") }).isEqualTo(emptyList<String>())
        assertThat(outcomes.toSet().size).isEqualTo(50)
        assertThat(backend.exchanges.map { it.status }).doesNotContain(500)
    }
}
