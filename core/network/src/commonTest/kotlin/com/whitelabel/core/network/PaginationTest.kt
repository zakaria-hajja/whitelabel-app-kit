package com.whitelabel.core.network

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.whitelabel.core.common.DataError
import com.whitelabel.core.common.Result
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test

@Serializable
private data class Item(val id: Int)

private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

class PaginationTest {
    @Test fun `page result returns items and pagination meta`() = runTest {
        val client = testClient {
            respond(
                """{"success":true,"data":[{"id":1},{"id":2}],"meta":{"requestId":"r","pagination":{"page":1,"pageSize":20,"totalItems":57,"totalPages":3}}}""",
                HttpStatusCode.OK, jsonHeaders,
            )
        }
        assertThat(client.getPageResult<Item>("items", mapOf("page" to 1))).isEqualTo(
            Result.Success(Page(listOf(Item(1), Item(2)), page = 1, pageSize = 20, totalItems = 57, totalPages = 3)),
        )
    }

    @Test fun `page past the end is an empty success`() = runTest {
        val client = testClient {
            respond(
                """{"success":true,"data":[],"meta":{"requestId":"r","pagination":{"page":9,"pageSize":20,"totalItems":57,"totalPages":3}}}""",
                HttpStatusCode.OK, jsonHeaders,
            )
        }
        assertThat(client.getPageResult<Item>("items")).isEqualTo(
            Result.Success(Page(emptyList<Item>(), page = 9, pageSize = 20, totalItems = 57, totalPages = 3)),
        )
    }

    @Test fun `missing pagination meta maps to SERIALIZATION`() = runTest {
        val client = testClient {
            respond("""{"success":true,"data":[{"id":1}],"meta":{"requestId":"r"}}""", HttpStatusCode.OK, jsonHeaders)
        }
        assertThat(client.getPageResult<Item>("items")).isEqualTo(Result.Error(DataError.Network.SERIALIZATION))
    }
}
