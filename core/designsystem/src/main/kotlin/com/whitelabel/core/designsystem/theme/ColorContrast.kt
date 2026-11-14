package com.whitelabel.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** A foreground/background color pair that must stay legible, with a readable [name]. */
data class ContrastPair(val name: String, val foreground: Color, val background: Color)

/** WCAG 2.x contrast ratio between two colors, from 1.0 (identical) to 21.0 (black on white). */
fun contrastRatio(foreground: Color, background: Color): Double {
    val a = foreground.luminance().toDouble()
    val b = background.luminance().toDouble()
    val lighter = maxOf(a, b)
    val darker = minOf(a, b)
    return (lighter + 0.05) / (darker + 0.05)
}

/** The `on*`-over-base pairs among the brand color roles. */
fun ColorScheme.contrastPairs(): List<ContrastPair> = listOf(
    ContrastPair("onPrimary/primary", onPrimary, primary),
    ContrastPair("onPrimaryContainer/primaryContainer", onPrimaryContainer, primaryContainer),
    ContrastPair("onSecondary/secondary", onSecondary, secondary),
    ContrastPair("onSecondaryContainer/secondaryContainer", onSecondaryContainer, secondaryContainer),
    ContrastPair("onTertiary/tertiary", onTertiary, tertiary),
    ContrastPair("onTertiaryContainer/tertiaryContainer", onTertiaryContainer, tertiaryContainer),
    ContrastPair("onError/error", onError, error),
    // Error banners and validation summaries.
    ContrastPair("onErrorContainer/errorContainer", onErrorContainer, errorContainer),
    ContrastPair("onBackground/background", onBackground, background),
    ContrastPair("onSurface/surface", onSurface, surface),
    ContrastPair("onSurfaceVariant/surfaceVariant", onSurfaceVariant, surfaceVariant),
    // Bottom-bar labels, dialog text, snackbar text.
    ContrastPair("onSurfaceVariant/surfaceContainer", onSurfaceVariant, surfaceContainer),
    ContrastPair("onSurface/surfaceContainerHigh", onSurface, surfaceContainerHigh),
    ContrastPair("inverseOnSurface/inverseSurface", inverseOnSurface, inverseSurface),
    // Text on the fixed roles; the variant is checked on the darker dim tone, its weakest pairing.
    ContrastPair("onPrimaryFixed/primaryFixed", onPrimaryFixed, primaryFixed),
    ContrastPair("onPrimaryFixedVariant/primaryFixedDim", onPrimaryFixedVariant, primaryFixedDim),
    ContrastPair("onSecondaryFixed/secondaryFixed", onSecondaryFixed, secondaryFixed),
    ContrastPair("onSecondaryFixedVariant/secondaryFixedDim", onSecondaryFixedVariant, secondaryFixedDim),
    ContrastPair("onTertiaryFixed/tertiaryFixed", onTertiaryFixed, tertiaryFixed),
    ContrastPair("onTertiaryFixedVariant/tertiaryFixedDim", onTertiaryFixedVariant, tertiaryFixedDim),
)
