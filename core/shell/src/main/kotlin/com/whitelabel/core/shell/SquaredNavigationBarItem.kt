package com.whitelabel.core.shell

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemColors
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.map

internal const val TAB_INDICATOR_TAG = "ShellTabIndicator"

/**
 * Material's [NavigationBarItem] with a squared indicator (BottomBarItemStyle.Squared). M3 has no
 * indicator shape and clips its press ripple to its own pill, so the caller passes a transparent
 * indicator colour, Material's ripple is turned off for the item, and the icon slot draws the
 * indicator and the ripple at Material's 56 x 32 with the pack's small corners.
 */
@Composable
internal fun RowScope.SquaredNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: @Composable () -> Unit,
    colors: NavigationBarItemColors,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val placement = remember { IndicatorPlacement() }
    val indicatorSource = remember(interactionSource) { IndicatorInteractionSource(interactionSource, placement) }
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        NavigationBarItem(
            selected = selected,
            onClick = onClick,
            icon = {
                CompositionLocalProvider(LocalRippleConfiguration provides RippleConfiguration()) {
                    SquaredIndicator(selected, indicatorSource, placement, icon)
                }
            },
            label = label,
            colors = colors,
            interactionSource = interactionSource,
            modifier = Modifier.onPlaced { placement.item = it },
        )
    }
}

/**
 * The 56 x 32 indicator, behind the 24 dp icon and centred on it, without changing the icon slot's
 * size (Material places the label by it). The press ripple is clipped to the same shape.
 */
@Composable
private fun SquaredIndicator(
    selected: Boolean,
    interactionSource: InteractionSource,
    placement: IndicatorPlacement,
    icon: @Composable () -> Unit,
) {
    val alpha by animateFloatAsState(if (selected) 1f else 0f, label = "indicator")
    val color = MaterialTheme.colorScheme.secondaryContainer
    val shape = MaterialTheme.shapes.small
    Box(
        contentAlignment = Alignment.Center,
        modifier = if (selected) Modifier.testTag(TAB_INDICATOR_TAG) else Modifier,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(Constraints.fixed(56.dp.roundToPx(), 32.dp.roundToPx()))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place((constraints.maxWidth - placeable.width) / 2, (constraints.maxHeight - placeable.height) / 2)
                    }
                }
                .onPlaced { placement.indicator = it }
                .drawBehind { drawOutline(shape.createOutline(size, layoutDirection, this), color, alpha = alpha) }
                .clip(shape)
                .indication(interactionSource, ripple()),
        )
        icon()
    }
}

/** Where the item and its indicator were last placed, read when a press arrives. */
private class IndicatorPlacement {
    var item: LayoutCoordinates? = null
    var indicator: LayoutCoordinates? = null

    /** The indicator's top-left in the item's coordinates. */
    fun indicatorOffset(): Offset {
        val item = item ?: return Offset.Zero
        val indicator = indicator ?: return Offset.Zero
        if (!item.isAttached || !indicator.isAttached) return Offset.Zero
        return item.localPositionOf(indicator, Offset.Zero)
    }
}

/**
 * The item's interactions with press positions moved into the indicator's coordinates, so the
 * ripple starts under the finger (Material does the same for its pill).
 */
private class IndicatorInteractionSource(
    underlying: InteractionSource,
    private val placement: IndicatorPlacement,
) : InteractionSource {
    private val presses = mutableMapOf<PressInteraction.Press, PressInteraction.Press>()

    override val interactions = underlying.interactions.map { interaction ->
        when (interaction) {
            is PressInteraction.Press ->
                PressInteraction.Press(interaction.pressPosition - placement.indicatorOffset()).also { presses[interaction] = it }
            is PressInteraction.Release -> presses.remove(interaction.press)?.let { PressInteraction.Release(it) } ?: interaction
            is PressInteraction.Cancel -> presses.remove(interaction.press)?.let { PressInteraction.Cancel(it) } ?: interaction
            else -> interaction
        }
    }
}
