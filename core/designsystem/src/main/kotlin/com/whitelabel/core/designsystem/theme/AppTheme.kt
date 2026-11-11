package com.whitelabel.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

internal val LocalStylePack = staticCompositionLocalOf { StylePack.Material }
internal val LocalReducedMotion = staticCompositionLocalOf { false }

/**
 * Applies the brand colors and fonts (resources) and the [stylePack] (everything else). With the
 * system's "Remove animations" on, the pack's motion is [StylePack.reduced]. Dynamic color is
 * deliberately not used.
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    stylePack: StylePack = StylePack.Material,
    content: @Composable () -> Unit,
) {
    val reducedMotion = systemReducedMotion()
    val pack = if (reducedMotion) stylePack.reduced() else stylePack
    CompositionLocalProvider(
        LocalSpacing provides Spacing(),
        LocalStylePack provides pack,
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(
            colorScheme = brandColorScheme(darkTheme),
            typography = brandTypography(pack.type),
            shapes = brandShapes(pack.corners),
            content = content,
        )
    }
}

object AppTheme {
    val spacing: Spacing
        @Composable
        @ReadOnlyComposable
        get() = LocalSpacing.current

    /** The active style pack, already reduced when the system removes animations. */
    val style: StylePack
        @Composable
        @ReadOnlyComposable
        get() = LocalStylePack.current

    /** Whether the system's "Remove animations" is on: stop ambient animations when true. */
    val reducedMotion: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalReducedMotion.current
}
