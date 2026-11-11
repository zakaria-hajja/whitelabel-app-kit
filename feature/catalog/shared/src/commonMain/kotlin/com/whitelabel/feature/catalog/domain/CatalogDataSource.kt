package com.whitelabel.feature.catalog.domain

import com.whitelabel.core.common.DataError
import com.whitelabel.core.common.Result
import com.whitelabel.core.network.Page
import com.whitelabel.feature.catalog.CatalogSort

data class CatalogItem(
    val id: String,
    val title: String,
    val description: String?,
    val category: String?,
    val imageUrl: String?,
)

/** What the list shows: [text] is the search (blank means none). */
data class CatalogQuery(
    val text: String = "",
    val category: String? = null,
    val sort: CatalogSort,
)

/** `GET catalog` (paginated) and `GET catalog/{id}`; both need a session. */
interface CatalogDataSource {
    suspend fun page(query: CatalogQuery, page: Int, pageSize: Int): Result<Page<CatalogItem>, DataError>

    suspend fun item(id: String): Result<CatalogItem, DataError>
}
