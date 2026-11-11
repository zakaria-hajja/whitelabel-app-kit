package com.whitelabel.feature.catalog

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.navigation.navDeepLink
import com.whitelabel.core.shell.AppDefinitionBuilder
import com.whitelabel.core.shell.FeatureEntry
import com.whitelabel.core.shell.Tab
import com.whitelabel.core.shell.screen
import com.whitelabel.feature.catalog.presentation.detail.CatalogDetailRoot
import com.whitelabel.feature.catalog.presentation.list.CatalogListRoot
import kotlinx.serialization.Serializable

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

/** Root of the catalog tab; public so an app can name it (start tab, home shortcut). */
@Serializable
data object CatalogRoute

/** One item; also opened by `<scheme>://catalog/{id}`. */
@Serializable
internal data class CatalogDetailRoute(val id: String)

/**
 * Adds the catalog tab: a paginated list with search, category chips and sort, and an item screen
 * that `<app.deepLinkScheme>://catalog/{id}` opens. Needs `auth()`. Its flag key is `catalog`, which
 * the backend also enforces: switched off, the tab is hidden and the API answers `FEATURE_DISABLED`.
 */
fun AppDefinitionBuilder.catalog(options: CatalogOptions = CatalogOptions()) {
    add(
        FeatureEntry(
            id = "catalog",
            flagKey = "catalog",
            requiresSignIn = true,
            tab = Tab(CatalogRoute, R.string.catalog_tab, Icons.AutoMirrored.Filled.List),
            routes = { scope ->
                screen<CatalogRoute> {
                    CatalogListRoot(options = options, onItemClick = { id -> scope.navigator.navigate(CatalogDetailRoute(id)) })
                }
                screen<CatalogDetailRoute>(
                    deepLinks = listOf(navDeepLink<CatalogDetailRoute>(basePath = "${scope.deepLinkScheme}://catalog")),
                ) {
                    CatalogDetailRoot(onBack = scope.navigator::back)
                }
            },
        ),
    )
}

