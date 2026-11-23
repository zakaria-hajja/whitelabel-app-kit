package com.whitelabel.core.designsystem.component

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.whitelabel.core.designsystem.theme.AppTheme
import com.whitelabel.core.designsystem.theme.StylePack

/** Where an [AppActionScaffold]'s content sits when it is shorter than the space above the dock. */
enum class ActionScaffoldAlignment {
    /** At the top, as a form. */
    Top,

    /** Centred in the space between the top bar and the dock, as a hero moment. Taller content scrolls from the top. */
    Center,
}

private val DockTopPadding = 12.dp
private val DockBottomPadding = 16.dp

/** Under this much height above the keyboard, the dock keeps only its message and primary. */
private val CompactKeyboardHeight = 360.dp

/**
 * A full screen whose actions stay pinned at the bottom in an action dock, outside the scroll, on
 * the page background. Use it for full screens with a primary action; not for tab roots, sheets,
 * dialogs, centred empty or error states, or keypads.
 *
 * The dock stacks, top to bottom: [message] (a live region, for an error line), [primary] (full
 * width), at most one [secondary] and [finePrint]. The primary never leaves the dock. When the dock
 * would take more than `Sizing.dockMaxFraction` of the height, the fine print and then the secondary
 * move to the end of the content. While the content can scroll further down, a
 * `Sizing.dockFadeHeight` fade of the background sits above the dock. [snackbarHost] shows 8 dp
 * above the dock.
 *
 * The scaffold owns the window insets (top and sides around everything, bottom inside the dock),
 * so callers must not add their own `safeDrawingPadding()`. The dock sits on the keyboard; with less
 * than 360 dp left above it, the fine print and the secondary move to the content. The dock comes
 * last in the reading order.
 */
@Composable
fun AppActionScaffold(
    primary: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    message: (@Composable () -> Unit)? = null,
    secondary: (@Composable () -> Unit)? = null,
    finePrint: (@Composable () -> Unit)? = null,
    contentAlignment: ActionScaffoldAlignment = ActionScaffoldAlignment.Top,
    scrollState: ScrollState = rememberScrollState(),
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val sizing = AppTheme.style.sizing
    val spacing = AppTheme.spacing
    val background = MaterialTheme.colorScheme.background
    val density = LocalDensity.current
    val heights = remember { DockPartHeights() }
    // Movable, so a part that moves between the dock and the content keeps its state.
    val secondaryPart = remember(secondary) { secondary?.let { movableContentOf(it) } }
    val finePrintPart = remember(finePrint) { finePrint?.let { movableContentOf(it) } }
    val bottomInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .testTag("AppActionScaffold")
            .background(background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        // With the keyboard up, the window effectively ends at the keyboard's top: the rule then
        // weighs the dock against the height left above it, and the keyboard isn't dock chrome.
        val keyboard = WindowInsets.ime.getBottom(density)
        val available = if (constraints.hasBoundedHeight) constraints.maxHeight - keyboard else Int.MAX_VALUE
        val overflow = with(density) {
            dockOverflow(
                available = available,
                chrome = (DockTopPadding + DockBottomPadding).roundToPx() +
                    (bottomInsets.getBottom(density) - keyboard).coerceAtLeast(0),
                gap = spacing.sm.roundToPx(),
                message = if (message != null) heights.message else 0,
                primary = heights.primary,
                secondary = if (secondary != null) heights.secondary else 0,
                finePrint = if (finePrint != null) heights.finePrint else 0,
                maxFraction = sizing.dockMaxFraction,
                // Little room above the keyboard: only the primary (and message) stay in the dock.
                compact = keyboard > 0 && available < CompactKeyboardHeight.roundToPx(),
            )
        }
        val secondaryInDock = overflow != DockOverflow.FinePrintAndSecondary
        val finePrintInDock = overflow == DockOverflow.None

        Column(Modifier.fillMaxSize()) {
            topBar()
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val viewport = constraints.maxHeight
                Column(
                    Modifier
                        .fillMaxSize()
                        .testTag("AppActionScaffoldContent")
                        .verticalScroll(scrollState),
                ) {
                    ViewportLayout(
                        viewport = viewport,
                        centred = contentAlignment == ActionScaffoldAlignment.Center,
                        bottomPadding = with(density) { spacing.lg.roundToPx() },
                    ) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = sizing.screenGutter)) {
                            content()
                            val movedSecondary = secondaryPart != null && !secondaryInDock
                            val movedFinePrint = finePrintPart != null && !finePrintInDock
                            if (movedSecondary || movedFinePrint) {
                                DockStack(spacing.sm, Modifier.fillMaxWidth().padding(top = spacing.lg)) {
                                    if (movedSecondary) Part({ heights.secondary = it }) { secondaryPart?.invoke() }
                                    if (movedFinePrint) Part({ heights.finePrint = it }) { finePrintPart?.invoke() }
                                }
                            }
                        }
                    }
                }
                DockFade(scrollState, Modifier.align(Alignment.BottomCenter))
                Box(Modifier.align(Alignment.BottomCenter).padding(bottom = spacing.sm)) { snackbarHost() }
            }
            DockStack(
                spacing.sm,
                Modifier
                    .fillMaxWidth()
                    .testTag("AppActionDock")
                    .background(background)
                    .windowInsetsPadding(bottomInsets)
                    .padding(
                        start = sizing.screenGutter,
                        end = sizing.screenGutter,
                        top = DockTopPadding,
                        bottom = DockBottomPadding,
                    ),
            ) {
                if (message != null) {
                    Part({ heights.message = it }, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) { message() }
                }
                Box(
                    Modifier.fillMaxWidth().onSizeChanged { heights.primary = it.height },
                    propagateMinConstraints = true,
                ) { primary() }
                if (secondaryPart != null && secondaryInDock) Part({ heights.secondary = it }) { secondaryPart() }
                if (finePrintPart != null && finePrintInDock) Part({ heights.finePrint = it }) { finePrintPart() }
            }
        }
    }
}

/** The measured heights of the dock's parts in px, wherever each is shown (same width in both places). */
@Stable
private class DockPartHeights {
    var message by mutableIntStateOf(0)
    var primary by mutableIntStateOf(0)
    var secondary by mutableIntStateOf(0)
    var finePrint by mutableIntStateOf(0)
}

@Composable
private fun Part(onHeight: (Int) -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.onSizeChanged { onHeight(it.height) }, contentAlignment = Alignment.Center) { content() }
}

/**
 * Stacks the dock's parts, centred, [gap] apart. A part of height 0 (a slot whose content shows
 * nothing, such as an error line with no error) adds no gap, as `dockOverflow` counts it.
 */
@Composable
private fun DockStack(gap: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val gapPx = gap.roundToPx()
        val shown = placeables.count { it.height > 0 }
        val height = placeables.sumOf { it.height } + gapPx * (shown - 1).coerceAtLeast(0)
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else placeables.maxOfOrNull { it.width } ?: 0
        layout(width, height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            var y = 0
            var placedAny = false
            placeables.forEach { placeable ->
                if (placeable.height > 0) {
                    if (placedAny) y += gapPx
                    placedAny = true
                }
                placeable.place((width - placeable.width) / 2, y)
                y += placeable.height
            }
        }
    }
}

/**
 * Lays the content out with [bottomPadding] below it. When [centred] and the content (with that
 * padding) fits in [viewport], it fills the viewport and the content sits in its middle.
 */
@Composable
private fun ViewportLayout(viewport: Int, centred: Boolean, bottomPadding: Int, content: @Composable () -> Unit) {
    Layout(content) { measurables, constraints ->
        val placeable = measurables.single().measure(constraints.copy(minHeight = 0))
        val fits = placeable.height + bottomPadding <= viewport
        val height = if (centred && fits) viewport else placeable.height + bottomPadding
        val y = if (centred && fits) (viewport - placeable.height) / 2 else 0
        layout(placeable.width, height) { placeable.place(0, y) }
    }
}

/** The background fading in over the content just above the dock, while there is more to scroll to. */
@Composable
private fun DockFade(scrollState: ScrollState, modifier: Modifier = Modifier) {
    // maxValue is Int.MAX_VALUE until the content is first measured: no fade before that.
    val shown by remember(scrollState) {
        derivedStateOf { scrollState.maxValue != Int.MAX_VALUE && showsDockFade(scrollState.canScrollForward) }
    }
    val alpha = animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = if (AppTheme.reducedMotion) snap() else tween(FadeMillis),
        label = "dockFade",
    )
    val composed by remember { derivedStateOf { alpha.value > 0f } }
    if (!shown && !composed) return
    val background = MaterialTheme.colorScheme.background
    Box(
        modifier
            .fillMaxWidth()
            .height(AppTheme.style.sizing.dockFadeHeight)
            .testTag("AppActionDockFade")
            .graphicsLayer { this.alpha = alpha.value }
            .background(Brush.verticalGradient(listOf(background.copy(alpha = 0f), background))),
    )
}

private const val FadeMillis = 150

@Composable
private fun PreviewDock(
    rows: Int,
    loading: Boolean = false,
    alignment: ActionScaffoldAlignment = ActionScaffoldAlignment.Top,
    withExtras: Boolean = true,
) {
    AppTheme(stylePack = StylePack.Gallery) {
        AppActionScaffold(
            primary = { AppPrimaryButton("Continue", {}, loading = loading) },
            topBar = { Text("Title", Modifier.padding(AppTheme.spacing.md), style = MaterialTheme.typography.titleLarge) },
            secondary = if (withExtras) ({ AppTextButton("I don't remember", {}) }) else null,
            finePrint = if (withExtras) {
                { Text("Everything stays on this phone.", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center) }
            } else {
                null
            },
            contentAlignment = alignment,
        ) {
            if (alignment == ActionScaffoldAlignment.Center) {
                Box(Modifier.size(120.dp).background(MaterialTheme.colorScheme.primaryContainer).align(Alignment.CenterHorizontally))
            }
            repeat(rows) { Text("Row ${it + 1}", Modifier.padding(vertical = AppTheme.spacing.sm)) }
        }
    }
}

@Preview(name = "Short form", heightDp = 640)
@Preview(name = "Short form dark", heightDp = 640, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ActionScaffoldShortPreview() = PreviewDock(rows = 3)

@Preview(name = "Long form with fade", heightDp = 640)
@Preview(name = "Long form with fade dark", heightDp = 640, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ActionScaffoldLongPreview() = PreviewDock(rows = 30)

@Preview(name = "Hero centred", heightDp = 640)
@Preview(name = "Hero centred dark", heightDp = 640, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ActionScaffoldHeroPreview() = PreviewDock(rows = 1, alignment = ActionScaffoldAlignment.Center, withExtras = false)

@Preview(name = "200 %", heightDp = 640, fontScale = 2f)
@Preview(name = "200 % dark", heightDp = 640, fontScale = 2f, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ActionScaffoldLargeTextPreview() = PreviewDock(rows = 30)

@Preview(name = "Loading primary", heightDp = 640)
@Preview(name = "Loading primary dark", heightDp = 640, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ActionScaffoldLoadingPreview() = PreviewDock(rows = 3, loading = true, withExtras = false)
