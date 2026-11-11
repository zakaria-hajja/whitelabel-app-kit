package com.whitelabel.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/** A cubic-bezier easing curve, as in CSS `cubic-bezier(a, b, c, d)`. */
data class CubicBezier(val a: Float, val b: Float, val c: Float, val d: Float)

/** One animation curve: a timed tween or a physics spring. Plain data, so packs compare by value. */
sealed interface MotionCurve {
    data class Tween(val durationMs: Int, val easing: CubicBezier) : MotionCurve
    data class Spring(val dampingRatio: Float, val stiffness: Float) : MotionCurve
}

/**
 * The named motion of a style pack. [screenEnter]/[screenExit] are pushes and pops,
 * [sharedElement] moves elements between screens, [tabSwitch] (with [tabSwitchScale]) is the
 * fade-through between tabs, [emphasis] is for small interactions, [snackbar] for feedback.
 */
data class MotionSpec(
    val screenEnter: MotionCurve,
    val screenExit: MotionCurve,
    val sharedElement: MotionCurve,
    val tabSwitch: MotionCurve,
    val tabSwitchScale: Float,
    val emphasis: MotionCurve,
    val snackbar: MotionCurve,
) {
    companion object {
        private val FastOutSlowIn = CubicBezier(0.4f, 0f, 0.2f, 1f)
        private val Standard = CubicBezier(0.2f, 0f, 0f, 1f)
        private val Emphasized = CubicBezier(0.2f, 0f, 0f, 1f)
        private val EmphasizedAccelerate = CubicBezier(0.3f, 0f, 0.8f, 0.15f)
        private val Linear = CubicBezier(0f, 0f, 1f, 1f)

        /** Navigation Compose's default transitions (a 700 ms fade), i.e. how apps behaved before packs. */
        val Material = MotionSpec(
            screenEnter = MotionCurve.Tween(700, FastOutSlowIn),
            screenExit = MotionCurve.Tween(700, FastOutSlowIn),
            sharedElement = MotionCurve.Tween(300, FastOutSlowIn),
            tabSwitch = MotionCurve.Tween(700, FastOutSlowIn),
            tabSwitchScale = 1f,
            emphasis = MotionCurve.Spring(dampingRatio = 1f, stiffness = 400f),
            snackbar = MotionCurve.Tween(250, FastOutSlowIn),
        )

        /** Soft, quick motion: shared elements and pushes at 420 ms, a short tab fade-through. */
        val Gallery = MotionSpec(
            screenEnter = MotionCurve.Tween(420, Emphasized),
            screenExit = MotionCurve.Tween(320, EmphasizedAccelerate),
            sharedElement = MotionCurve.Tween(420, Emphasized),
            tabSwitch = MotionCurve.Tween(210, Standard),
            tabSwitchScale = 0.96f,
            emphasis = MotionCurve.Spring(dampingRatio = 0.55f, stiffness = 380f),
            snackbar = MotionCurve.Tween(200, Standard),
        )

        /** "Remove animations": every movement becomes a short crossfade. */
        val Reduced: MotionSpec = MotionCurve.Tween(150, Linear).let { fade ->
            MotionSpec(fade, fade, fade, fade, tabSwitchScale = 1f, emphasis = fade, snackbar = fade)
        }
    }
}

/** The Compose animation spec for this curve. */
fun <T> MotionCurve.toAnimationSpec(): FiniteAnimationSpec<T> = when (this) {
    is MotionCurve.Tween -> tween(durationMs, easing = CubicBezierEasing(easing.a, easing.b, easing.c, easing.d))
    is MotionCurve.Spring -> spring(dampingRatio = dampingRatio, stiffness = stiffness)
}
