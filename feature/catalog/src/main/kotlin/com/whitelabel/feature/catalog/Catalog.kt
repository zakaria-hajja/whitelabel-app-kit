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
            tab = Tab(CatalogRoute, R.string.catalog_tab, Icons.AutoMirrored.Filled.List, routeId = CatalogRoutes.LIST),
            modules = listOf(catalogModule),
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

