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
    /** How screen top bars look (`AppScaffold`). */
    val topBar: TopBarStyle = TopBarStyle.Raised,
    /** How section titles look (`AppSectionHeader`). */
    val sectionHeader: SectionHeaderStyle = SectionHeaderStyle.Accent,
    /** How a setting and its value sit in a row (`SettingsValueRow`). */
    val valueRows: ValueRowLayout = ValueRowLayout.Inline,
    /**
     * How the shell's bottom bar is filled. [BottomBarStyle.Surface] needs [SurfaceStyle.Hairline]
     * and is ignored with [SurfaceStyle.Shadow]. Defaults to [BottomBarStyle.Container], so
     * existing apps keep their bar.
     */
    val bottomBar: BottomBarStyle = BottomBarStyle.Container,
    /** How buttons look (`AppPrimaryButton`, `AppSecondaryButton`, `AppTextButton`). Defaults to Material's pill. */
    val buttons: ButtonStyle = ButtonStyle.Pill,
    /** How the shell's bottom bar marks the selected tab. Defaults to Material's. */
    val bottomBarItems: BottomBarItemStyle = BottomBarItemStyle.Material,
    /** The sheet drag handle and unselected radio buttons (`AppSheetDragHandle`, `appRadioButtonColors`). */
    val selectionControls: SelectionControlStyle = SelectionControlStyle.Material,
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
            topBar = TopBarStyle.Raised,
            sectionHeader = SectionHeaderStyle.Accent,
            valueRows = ValueRowLayout.Inline,
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
            topBar = TopBarStyle.Flat,
            sectionHeader = SectionHeaderStyle.Caption,
            valueRows = ValueRowLayout.Stacked,
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

/**
 * Corner radii for Material's shape scale. [small], [medium] and [large] drive buttons, fields and
 * cards. [extraSmall] (snackbars, menus, tooltips) and [extraLarge] (dialogs, bottom sheets) are
 * optional: `null` keeps Material's 4 and 28 dp, so a pack only changes them when an app opts in.
 */
data class Corners(
    val small: Dp,
    val medium: Dp,
    val large: Dp,
    val extraSmall: Dp? = null,
    val extraLarge: Dp? = null,
)

/** How containers separate from the background: elevation shadows, or 1 dp hairline outlines. */
enum class SurfaceStyle { Shadow, Hairline }

/** A screen's top bar. */
enum class TopBarStyle {
    /** Material's top app bar on the surface color, title in `titleLarge`. */
    Raised,

    /** On the page background with no elevation or scroll tint, title in `headlineSmall`. */
    Flat,
}

/** A section title. */
enum class SectionHeaderStyle {
    /** `titleSmall` in the primary color, as written. */
    Accent,

    /** A small caption: `labelSmall`, uppercase, letter-spaced, in `onSurfaceVariant`. */
    Caption,
}

/** A setting's label and value. */
enum class ValueRowLayout {
    /** The label at the start, the value at the end of the same line. */
    Inline,

    /** The label, then the value under it in a muted color. */
    Stacked,

    /**
     * The label at the start, the value hugging the trailing edge, then a chevron when the row opens
     * something (iOS's disclosure row). A read-only row has no chevron.
     */
    Disclosure,
}

/** Whether a row in this layout ends with a chevron. */
fun ValueRowLayout.showsChevron(clickable: Boolean): Boolean = this == ValueRowLayout.Disclosure && clickable

/** Buttons. */
enum class ButtonStyle {
    /** Material's: fully rounded, Material's label weight and outlined colors. */
    Pill,

    /**
     * Corners from `shapes.small`, 600-weight labels; the outlined button takes a 1 dp `outline`
     * border and a `primary` label, so its edge reaches 3:1.
     */
    Squared,
}

/** How the bottom bar marks the selected tab. */
enum class BottomBarItemStyle {
    /** Material's: a pill indicator, the selected label in `onSurfaceVariant`. */
    Material,

    /** An indicator with `shapes.small` corners and the selected label in `onSurface`. */
    Squared,
}

/** The sheet drag handle and an unselected radio button. */
enum class SelectionControlStyle {
    /** Material's `onSurfaceVariant`. */
    Material,

    /** The quieter `outline` role (still 3:1 on the surface in a brand that sets it so). */
    Outline,
}

/** The shell's bottom navigation bar. */
enum class BottomBarStyle {
    /** Material's bar on `surfaceContainer`. */
    Container,

    /**
     * On the page's `surface`, set apart only by the hairline above it. Needs
     * [SurfaceStyle.Hairline]: a [SurfaceStyle.Shadow] pack has no hairline, so it keeps
     * [Container].
     */
    Surface,
}

data class Sizing(val controlHeight: Dp, val listRowMinHeight: Dp, val screenGutter: Dp)

/** Moments that may give haptic feedback. */
enum class HapticEvent { Confirm, Success, Toggle }

enum class HapticPattern { Tick, Confirm, Toggle }
