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
 * Decides whether a NavHost transition is a tab switch, by the tab each destination belongs to: a
 * tab root belongs to itself, and a pushed screen belongs to the tab it was pushed from. So tapping
 * another tab while on a pushed screen, or switching to a tab that restores its pushed screen, is a
 * [TransitionKind.TabSwitch]; pushes and pops within a tab are [TransitionKind.Screen]. Entries are
 * identified by their back-stack entry id; routes are matched to tab roots by name, ignoring arguments.
 * One instance lives as long as its NavHost.
 */
internal class TabTransitions(private val tabRoutes: Set<String>) {
    private val owners = HashMap<String, String>()

    private fun tabOf(route: String?): String? = route?.substringBefore('?')?.substringBefore('/')?.takeIf { it in tabRoutes }

    private fun owner(id: String, route: String?): String? = tabOf(route) ?: owners[id]

    fun kind(fromId: String, fromRoute: String?, toId: String, toRoute: String?): TransitionKind {
        val from = owner(fromId, fromRoute)
        // A screen seen for the first time is being pushed from the current tab.
        val to = owner(toId, toRoute) ?: from
        if (to != null && tabOf(toRoute) == null) owners[toId] = to
        return if (from != null && to != null && from != to) TransitionKind.TabSwitch else TransitionKind.Screen
    }
}

/** NavHost transitions from the pack's motion: a fade-through between tabs, a fade for pushes and pops. */
internal class ShellTransitions(private val motion: MotionSpec, tabRoutes: Set<String>) {

    private val tabs = TabTransitions(tabRoutes)

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.kind() =
        tabs.kind(initialState.id, initialState.destination.route, targetState.id, targetState.destination.route)

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
