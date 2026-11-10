package com.whitelabel.core.shell

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.whitelabel.core.designsystem.component.EmptyState

const val BOTTOM_BAR_TAG = "ShellBottomBar"

/**
 * The app's root navigation: every entry's graph, starting at the resolved start tab, with a
 * bottom bar when two or more tabs are visible. No visible tab shows an empty state.
 */
@Composable
fun ShellNavHost(
    definition: AppDefinition,
    visibleTabs: List<Tab>,
    modifier: Modifier = Modifier,
) {
    val startTab = definition.resolveStartTab(visibleTabs)
    if (startTab == null) {
        EmptyState(
            title = stringResource(R.string.shell_no_features_title),
            message = stringResource(R.string.shell_no_features_message),
            modifier = modifier.fillMaxSize(),
        )
        return
    }
    val navController = rememberNavController()
    val uriHandler = LocalUriHandler.current
    val navigator = remember(navController, uriHandler) {
        object : AppNavigator {
            override fun toTab(route: Any) = navController.navigateToTab(route)
            override fun navigate(route: Any) = navController.navigate(route)
            override fun back() {
                navController.popBackStack()
            }
            override fun openUrl(url: String) = uriHandler.openUri(url)
        }
    }
    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (visibleTabs.size >= 2) BottomBar(visibleTabs, navController)
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startTab.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            definition.entries.forEach { entry -> entry.routes(this, navigator) }
        }
    }
}

@Composable
private fun BottomBar(tabs: List<Tab>, navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    NavigationBar(modifier = Modifier.testTag(BOTTOM_BAR_TAG)) {
        tabs.forEach { tab ->
            val label = stringResource(tab.label)
            NavigationBarItem(
                selected = destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true,
                onClick = { navController.navigateToTab(tab.route) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(label) },
            )
        }
    }
}

private fun NavHostController.navigateToTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
