package com.whitelabel.feature.catalog.data

import com.whitelabel.core.common.DataError
import com.whitelabel.core.common.Result
import com.whitelabel.core.common.map
import com.whitelabel.core.network.Page
import com.whitelabel.core.network.getPageResult
import com.whitelabel.core.network.getResult
import com.whitelabel.feature.catalog.domain.CatalogDataSource
import com.whitelabel.feature.catalog.domain.CatalogItem
import com.whitelabel.feature.catalog.domain.CatalogQuery
import io.ktor.client.HttpClient
import io.ktor.http.encodeURLParameter
import javax.inject.Inject
import kotlinx.serialization.Serializable

/** The backend allows a search of at most this many characters. */
internal const val MAX_QUERY_LENGTH = 100

@Serializable
private data class CatalogItemDto(
    val id: String,
    val title: String,
    val description: String? = null,
    val category: String? = null,
    val imageUrl: String? = null,
)

private fun CatalogItemDto.toItem() = CatalogItem(id, title, description, category, imageUrl)

internal class KtorCatalogDataSource @Inject constructor(
    private val client: HttpClient,
) : CatalogDataSource {

    override suspend fun page(query: CatalogQuery, page: Int, pageSize: Int): Result<Page<CatalogItem>, DataError> =
        client.getPageResult<CatalogItemDto>(
            "catalog",
            mapOf(
                "page" to page,
                "pageSize" to pageSize,
                "q" to query.text.trim().take(MAX_QUERY_LENGTH).ifEmpty { null },
                "category" to query.category,
                "sort" to query.sort.wire,
            ),
        ).map { result ->
            Page(result.items.map { it.toItem() }, result.page, result.pageSize, result.totalItems, result.totalPages)
        }

    // The id comes from a deep link too: encode it so it stays one path segment.
    override suspend fun item(id: String): Result<CatalogItem, DataError> =
        client.getResult<CatalogItemDto>("catalog/${id.encodeURLParameter()}").map { it.toItem() }
}
