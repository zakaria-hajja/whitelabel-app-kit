package com.whitelabel.feature.catalog.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.whitelabel.core.common.Result
import com.whitelabel.core.network.HttpClientFactory
import com.whitelabel.core.network.NetworkConfig
import com.whitelabel.core.network.Page
import com.whitelabel.core.network.SessionTokenStore
import com.whitelabel.core.network.SessionTokens
import com.whitelabel.feature.catalog.CatalogSort
import com.whitelabel.feature.catalog.domain.CatalogItem
import com.whitelabel.feature.catalog.domain.CatalogQuery
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

private object SignedIn : SessionTokenStore {
    override suspend fun current() = SessionTokens("access", "refresh")
    override suspend fun replace(expected: SessionTokens, new: SessionTokens) = false
    override suspend fun endSession(expected: SessionTokens) = Unit
}

private const val LIST =
    """{"success":true,"data":[{"category":"Music","createdAt":"2025-12-10T12:00:00.000Z","description":"Two","id":"i2","imageUrl":"https://example.com/2.png","title":"Item 2","updatedAt":"2025-12-11T08:15:00.000Z"},{"category":null,"createdAt":"2025-12-05T12:00:00.000Z","description":null,"id":"i1","imageUrl":null,"title":"Item 1","updatedAt":"2025-12-05T12:00:00.000Z"}],"meta":{"pagination":{"page":1,"pageSize":20,"totalItems":2,"totalPages":1},"requestId":"r"}}"""

class KtorCatalogDataSourceTest {

    private val requests = mutableListOf<HttpRequestData>()

    private fun source(body: String = LIST) = KtorCatalogDataSource(
        HttpClientFactory.create(
            MockEngine { requests += it; respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) },
            NetworkConfig("https://api.test/api/v1/", tenant = null, userAgent = "test"),
            SignedIn,
        ),
    )

    @Test fun `query, category and sort reach the request`() = runTest {
        val result = source().page(CatalogQuery(text = "jazz", category = "Music", sort = CatalogSort.TITLE_ASC), page = 2, pageSize = 10)
        val url = requests.single().url
        assertThat(url.encodedPath).isEqualTo("/api/v1/catalog")
        assertThat(url.parameters["q"]).isEqualTo("jazz")
        assertThat(url.parameters["category"]).isEqualTo("Music")
        assertThat(url.parameters["sort"]).isEqualTo("title:asc")
        assertThat(url.parameters["page"]).isEqualTo("2")
        assertThat(url.parameters["pageSize"]).isEqualTo("10")
        assertThat(result).isEqualTo(
            Result.Success(
                Page(
                    items = listOf(
                        CatalogItem("i2", "Item 2", "Two", "Music", "https://example.com/2.png"),
                        CatalogItem("i1", "Item 1", null, null, null),
                    ),
                    page = 1, pageSize = 20, totalItems = 2, totalPages = 1,
                ),
            ),
        )
    }

    @Test fun `blank query is omitted and long query is capped at 100`() = runTest {
        source().page(CatalogQuery(text = "   ", sort = CatalogSort.NEWEST), page = 1, pageSize = 20)
        source().page(CatalogQuery(text = "  " + "x".repeat(150) + "  ", sort = CatalogSort.NEWEST), page = 1, pageSize = 20)
        assertThat(requests[0].url.parameters["q"]).isNull()
        assertThat(requests[0].url.parameters["category"]).isNull()
        assertThat(requests[1].url.parameters["q"]).isEqualTo("x".repeat(100))
    }

    @Test fun `item id is encoded into the path`() = runTest {
        val one = """{"success":true,"data":{"category":null,"createdAt":"2025-12-05T12:00:00.000Z","description":null,"id":"a/b","imageUrl":null,"title":"T","updatedAt":"2025-12-05T12:00:00.000Z"},"meta":{"requestId":"r"}}"""
        source(one).item("a/b?c")
        assertThat(requests.single().url.encodedPath).isEqualTo("/api/v1/catalog/a%2Fb%3Fc")
    }
}
