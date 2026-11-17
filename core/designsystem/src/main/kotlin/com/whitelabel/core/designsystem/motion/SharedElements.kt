@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.whitelabel.core.designsystem.motion

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.whitelabel.core.designsystem.theme.AppTheme
import com.whitelabel.core.designsystem.theme.MotionCurve
import com.whitelabel.core.designsystem.theme.MotionSpec
import com.whitelabel.core.designsystem.theme.toAnimationSpec

/** The shell's shared-transition scope; `null` outside the shell. */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/** The current navigation destination's animation scope; `null` outside a `screen<Route>`. */
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Whether the shell's current navigation is a pop (Back or predictive back); the shell provides it, `false` outside the shell. */
val LocalNavIsPop = staticCompositionLocalOf<() -> Boolean> { { false } }

/**
 * Moves this element to the element with the same [key] on the next screen (same content, e.g. an
 * image or an avatar), with the style pack's `sharedElement` motion, `sharedElementReturn` on back.
 * Does nothing outside the shell.
 */
@Composable
fun Modifier.sharedElementTransition(key: Any): Modifier {
    val transition = LocalSharedTransitionScope.current ?: return this
    val visibility = LocalNavAnimatedVisibilityScope.current ?: return this
    val bounds = packBoundsTransform()
    return with(transition) {
        this@sharedElementTransition.sharedElement(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = visibility,
            boundsTransform = bounds,
        )
    }
}

/**
 * Morphs this container's bounds into the container with the same [key] on the next screen
 * (different content, e.g. a card into a page), with the pack's motion. Does nothing outside the shell.
 */
@Composable
fun Modifier.sharedBoundsTransition(key: Any): Modifier {
    val transition = LocalSharedTransitionScope.current ?: return this
    val visibility = LocalNavAnimatedVisibilityScope.current ?: return this
    val bounds = packBoundsTransform()
    return with(transition) {
        this@sharedBoundsTransition.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = visibility,
            boundsTransform = bounds,
        )
    }
}

/** The shared-element curve for the current navigation: [MotionSpec.sharedElementReturn] on a pop. */
internal fun MotionSpec.sharedCurve(isPop: Boolean): MotionCurve = if (isPop) sharedElementReturn else sharedElement

// The transform runs when the bounds animation starts, after the NavHost's transition lambdas
// have recorded the direction, so it reads it then rather than at composition.
@Composable
private fun packBoundsTransform(): BoundsTransform {
    val motion = AppTheme.style.motion
    val isPop = LocalNavIsPop.current
    return BoundsTransform { _, _ -> motion.sharedCurve(isPop()).toAnimationSpec() }
}
