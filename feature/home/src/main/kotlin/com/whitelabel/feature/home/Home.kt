package com.whitelabel.feature.home

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.composable
import com.whitelabel.core.shell.AppDefinitionBuilder
import com.whitelabel.core.shell.FeatureEntry
import com.whitelabel.core.shell.Tab
import com.whitelabel.feature.home.presentation.HomeRoot
import kotlinx.serialization.Serializable

/** A button on the home screen that opens another tab, given by its root route (e.g. `ProfileRoute`). */
data class HomeShortcut(
    @StringRes val label: Int,
    val icon: ImageVector,
    val tab: Any,
)

/** [shortcuts] are shown in order; one whose tab is missing or switched off is hidden. */
data class HomeOptions(val shortcuts: List<HomeShortcut> = emptyList())

/** Root of the home tab; public so an app can make it the start tab. */
@Serializable
data object HomeRoute

/** Adds the home tab: a greeting for the signed-in user and the app's shortcuts. Needs `auth()`. */
fun AppDefinitionBuilder.home(options: HomeOptions = HomeOptions()) {
    add(
        FeatureEntry(
            id = "home",
            requiresSignIn = true,
            tab = Tab(HomeRoute, R.string.home_tab, Icons.Default.Home),
            routes = { scope ->
                composable<HomeRoute> {
                    HomeRoot(options = options, canOpenTab = scope.navigator::canOpenTab, onOpenTab = scope.navigator::toTab)
                }
            },
        ),
    )
}
