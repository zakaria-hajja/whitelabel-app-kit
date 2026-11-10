package com.whitelabel.core.shell

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.core.net.toUri
import kotlinx.serialization.Serializable
import com.whitelabel.core.designsystem.component.EmptyState

internal const val BOTTOM_BAR_TAG = "ShellBottomBar"

/** Root of the sign-in graph when the app has a gate (`auth()`). */
@Serializable
internal data object AuthGraph

/** Root of the app's tabs when the app has a gate. */
@Serializable
internal data object MainGraph

/**
 * The app's root navigation, with a bottom bar when two or more tabs are visible.
 *
 * Without a gate, every entry's graph is registered and the app starts at the resolved start tab.
 * With a gate, the host holds two graphs: [AuthGraph] (the gate entry's routes) and [MainGraph]
 * (everything else). [signedIn] selects one, and a change replaces the whole back stack.
 *
 * [deepLink] is an incoming link the activity took out of its intent (so Navigation never opens it
 * on its own). It is opened, held until sign-in, or dropped per [deepLinkAction], and reported
 * through [onDeepLinkConsumed] once it is no longer pending.
 */
@Composable
internal fun ShellNavHost(
    definition: AppDefinition,
    visibleTabs: List<Tab>,
    deepLinkScheme: String,
    modifier: Modifier = Modifier,
    signedIn: Boolean = true,
    deepLink: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
    onScreenView: (String) -> Unit = {},
) {
    val gate = definition.gateEntry
    val showMain = gate == null || signedIn
    val startTab = definition.resolveStartTab(visibleTabs)
    if (startTab == null && showMain) {
        EmptyState(
            title = stringResource(R.string.shell_no_features_title),
            message = stringResource(R.string.shell_no_features_message),
            modifier = modifier.fillMaxSize(),
        )
        return
    }
    val uriHandler = LocalUriHandler.current
    val navigator = remember(navController, uriHandler, visibleTabs) {
        object : AppNavigator {
            override fun toTab(route: Any) = navController.navigateToTab(route)
            override fun canOpenTab(route: Any) = visibleTabs.any { it.route == route }
            override fun navigate(route: Any) = navController.navigate(route)
            override fun back() {
                navController.popBackStack()
            }
            override fun openUrl(url: String) = uriHandler.openUri(url)
        }
    }
    val scope = remember(navigator, deepLinkScheme) { FeatureScope(navigator, deepLinkScheme) }
    val currentOnScreenView by rememberUpdatedState(onScreenView)
    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            destination.route?.let { currentOnScreenView(screenName(it)) }
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }
    val currentOnDeepLinkConsumed by rememberUpdatedState(onDeepLinkConsumed)
    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (showMain && visibleTabs.size >= 2) BottomBar(visibleTabs, navController)
        },
    ) { innerPadding ->
        val hostModifier = Modifier
            .padding(innerPadding)
            // Consume what the shell padded for, so feature scaffolds don't pad for the system bars again.
            .consumeWindowInsets(innerPadding)
        if (gate == null) {
            NavHost(navController = navController, startDestination = checkNotNull(startTab).route, modifier = hostModifier) {
                definition.entries.forEach { entry -> entry.routes(this, scope) }
            }
        } else {
            NavHost(
                navController = navController,
                startDestination = if (signedIn) MainGraph else AuthGraph,
                modifier = hostModifier,
            ) {
                navigation<AuthGraph>(startDestination = checkNotNull(gate.gate).startRoute) { gate.routes(this, scope) }
                if (startTab != null) {
                    navigation<MainGraph>(startDestination = startTab.route) {
                        definition.entries.filter { it !== gate }.forEach { entry -> entry.routes(this, scope) }
                    }
                }
            }
        }
        LaunchedEffect(signedIn, deepLink) {
            if (gate != null) navController.showGraph(if (signedIn) MainGraph else AuthGraph)
            val link = deepLink ?: return@LaunchedEffect
            val uri = link.toUri()
            val graph = navController.graph
            val action = deepLinkAction(
                inMain = if (gate == null) graph.hasDeepLink(uri) else graph.findNode<MainGraph>()?.hasDeepLink(uri) == true,
                inAuth = gate != null && graph.findNode<AuthGraph>()?.hasDeepLink(uri) == true,
                signedIn = signedIn,
                hasGate = gate != null,
            )
            if (action == DeepLinkAction.HOLD) return@LaunchedEffect
            if (action == DeepLinkAction.OPEN) navController.navigate(uri)
            currentOnDeepLinkConsumed()
        }
    }
}

/** Shows [graph] with nothing behind it, unless the current destination is already inside it. */
private fun NavHostController.showGraph(graph: Any) {
    val inside = currentBackStackEntry?.destination?.hierarchy?.any { it.hasRoute(graph::class) } == true
    if (inside) return
    navigate(graph) {
        popUpTo(this@showGraph.graph.id) { inclusive = true }
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
