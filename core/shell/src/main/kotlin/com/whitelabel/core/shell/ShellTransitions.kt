package com.whitelabel.core.shell

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.whitelabel.core.designsystem.motion.LocalNavAnimatedVisibilityScope
import com.whitelabel.core.designsystem.theme.MotionSpec
import com.whitelabel.core.designsystem.theme.toAnimationSpec

internal enum class TransitionKind { TabSwitch, Screen }

/**
 * Tab to tab is a [TransitionKind.TabSwitch]; everything else (push, back, null routes) is a
 * [TransitionKind.Screen]. Routes are matched to tab roots by name, ignoring arguments.
 */
internal fun transitionKind(fromRoute: String?, toRoute: String?, tabRoutes: Set<String>): TransitionKind {
    fun isTab(route: String?) = route != null && route.substringBefore('?').substringBefore('/') in tabRoutes
    return if (isTab(fromRoute) && isTab(toRoute)) TransitionKind.TabSwitch else TransitionKind.Screen
}

/** NavHost transitions from the pack's motion: a fade-through between tabs, a fade for pushes and pops. */
internal class ShellTransitions(private val motion: MotionSpec, private val tabRoutes: Set<String>) {

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.kind() =
        transitionKind(initialState.destination.route, targetState.destination.route, tabRoutes)

    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        when (kind()) {
            TransitionKind.TabSwitch ->
                fadeIn(motion.tabSwitch.toAnimationSpec()) +
                    scaleIn(motion.tabSwitch.toAnimationSpec(), initialScale = motion.tabSwitchScale)
            TransitionKind.Screen -> fadeIn(motion.screenEnter.toAnimationSpec())
        }
    }

    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        when (kind()) {
            TransitionKind.TabSwitch -> fadeOut(motion.tabSwitch.toAnimationSpec())
            TransitionKind.Screen -> fadeOut(motion.screenExit.toAnimationSpec())
        }
    }
}

/**
 * Registers a destination like `composable<T>`, and exposes its animation scope so
 * `Modifier.sharedElementTransition(key)` / `sharedBoundsTransition(key)` work inside it.
 */
inline fun <reified T : Any> NavGraphBuilder.screen(
    deepLinks: List<NavDeepLink> = emptyList(),
    noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable<T>(deepLinks = deepLinks) { entry ->
        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) { content(entry) }
    }
}
