package com.whitelabel.feature.catalog

/** The catalog's sort orders, as the backend names them. */
enum class CatalogSort(val wire: String) {
    NEWEST("createdAt:desc"),
    OLDEST("createdAt:asc"),
    TITLE_ASC("title:asc"),
    TITLE_DESC("title:desc"),
}

/**
 * [categories] become filter chips (none: no chips), as the backend's catalog has no categories
 * endpoint. [pageSize] is 1–100 items per request; [defaultSort] is the order the list opens in.
 */
data class CatalogOptions(
    val categories: List<String> = emptyList(),
    val pageSize: Int = 20,
    val defaultSort: CatalogSort = CatalogSort.NEWEST,
) {
    init {
        require(pageSize in 1..100) { "CatalogOptions.pageSize must be 1–100: $pageSize" }
        categories.forEach { require(it.length in 1..50) { "CatalogOptions category must be 1–50 characters: '$it'" } }
    }
}
