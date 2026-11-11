package com.whitelabel.core.designsystem.theme

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Everything about an app's look except colors and fonts (those stay in brand resources): type
 * tweaks, corners, how surfaces separate, control sizing, motion and haptics. An app picks one in
 * its definition (`style(StylePack.Gallery)`) and can vary it with [copy].
 */
data class StylePack(
    val name: String,
    val type: TypeTweaks,
    /** `null` reads the `brand_corner_*` resources. */
    val corners: Corners?,
    val surfaces: SurfaceStyle,
    val sizing: Sizing,
    val motion: MotionSpec,
    /** Which feedback each event plays; an event not in the map plays nothing. */
    val haptics: Map<HapticEvent, HapticPattern>,
) {
    /** This pack with "Remove animations" applied: every motion becomes [MotionSpec.Reduced]. */
    fun reduced(): StylePack = copy(motion = MotionSpec.Reduced)

    companion object {
        /** Today's look: brand corner resources, 48 dp controls, shadowed cards, no haptics. */
        val Material = StylePack(
            name = "Material",
            type = TypeTweaks(displayWeight = null, headlineWeight = null, titlesUseBodyFont = false),
            corners = null,
            surfaces = SurfaceStyle.Shadow,
            sizing = Sizing(controlHeight = 48.dp, listRowMinHeight = 56.dp, screenGutter = 16.dp),
            motion = MotionSpec.Material,
            haptics = emptyMap(),
        )

        /** Editorial and quiet: small radii, hairlines instead of shadows, roomier controls, soft motion. */
        val Gallery = StylePack(
            name = "Gallery",
            type = TypeTweaks(displayWeight = FontWeight.Normal, headlineWeight = FontWeight.Normal, titlesUseBodyFont = true),
            corners = Corners(small = 4.dp, medium = 8.dp, large = 14.dp),
            surfaces = SurfaceStyle.Hairline,
            sizing = Sizing(controlHeight = 52.dp, listRowMinHeight = 72.dp, screenGutter = 20.dp),
            motion = MotionSpec.Gallery,
            haptics = mapOf(
                HapticEvent.Confirm to HapticPattern.Tick,
                HapticEvent.Success to HapticPattern.Confirm,
                HapticEvent.Toggle to HapticPattern.Toggle,
            ),
        )
    }
}

/** Changes to the Material type roles; fonts still come from `brand_display` / `brand_body`. */
data class TypeTweaks(
    val displayWeight: FontWeight?,
    val headlineWeight: FontWeight?,
    /** Title styles use the body font instead of the display font. */
    val titlesUseBodyFont: Boolean,
)

data class Corners(val small: Dp, val medium: Dp, val large: Dp)

/** How containers separate from the background: elevation shadows, or 1 dp hairline outlines. */
enum class SurfaceStyle { Shadow, Hairline }

data class Sizing(val controlHeight: Dp, val listRowMinHeight: Dp, val screenGutter: Dp)

/** Moments that may give haptic feedback. */
enum class HapticEvent { Confirm, Success, Toggle }

enum class HapticPattern { Tick, Confirm, Toggle }
