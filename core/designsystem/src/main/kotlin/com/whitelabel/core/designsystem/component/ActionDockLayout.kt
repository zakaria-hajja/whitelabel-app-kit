package com.whitelabel.core.designsystem.component

/** Which of the action dock's extras move to the end of the content. The primary never moves. */
enum class DockOverflow {
    /** Everything stays in the dock. */
    None,

    /** The fine print moves to the content. */
    FinePrint,

    /** The fine print and the secondary action move to the content. */
    FinePrintAndSecondary,
}

/**
 * Decides which extras leave the action dock. All heights are in px; a part of height 0 is absent
 * and adds no gap. [chrome] is the dock's top and bottom padding plus its bottom inset; [gap] sits
 * between two present parts. The dock may take [maxFraction] of [available]: over that, the fine
 * print moves first, then the secondary. [compact] (little room above the keyboard) moves both
 * whatever their height. Mirrored by `ActionDockLayout.overflow` in WhitelabelKit.
 */
internal fun dockOverflow(
    available: Int,
    chrome: Int,
    gap: Int,
    message: Int,
    primary: Int,
    secondary: Int,
    finePrint: Int,
    maxFraction: Float,
    compact: Boolean,
): DockOverflow {
    val hasExtras = secondary > 0 || finePrint > 0
    if (!hasExtras) return DockOverflow.None
    if (compact) return DockOverflow.FinePrintAndSecondary
    val cap = available * maxFraction
    fun height(vararg parts: Int): Int {
        val present = parts.filter { it > 0 }
        return chrome + present.sum() + gap * (present.size - 1).coerceAtLeast(0)
    }
    return when {
        height(message, primary, secondary, finePrint) <= cap -> DockOverflow.None
        finePrint > 0 && height(message, primary, secondary) <= cap -> DockOverflow.FinePrint
        else -> DockOverflow.FinePrintAndSecondary
    }
}

/** The fade over the content shows only while there is more content below the dock. */
internal fun showsDockFade(canScrollForward: Boolean): Boolean = canScrollForward
