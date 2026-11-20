package com.whitelabel.core.shell

import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.platform.testTag
import kotlin.math.roundToInt
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isLessThan
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.navigation.compose.composable
import com.whitelabel.core.designsystem.theme.AppTheme
import com.whitelabel.core.designsystem.theme.BottomBarItemStyle
import com.whitelabel.core.designsystem.theme.BottomBarStyle
import androidx.compose.ui.graphics.asAndroidBitmap
import com.whitelabel.core.designsystem.theme.StylePack
import com.whitelabel.core.shell.test.R
import kotlinx.serialization.Serializable
import org.junit.Rule
import org.junit.Test

@Serializable data object OneRoute
@Serializable data object TwoRoute
@Serializable data object ThreeRoute
@Serializable data object FourRoute

class ShellNavHostTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val one = FeatureEntry(
        id = "one",
        tab = Tab(OneRoute, R.string.test_tab_one, Icons.Default.Home),
        routes = { composable<OneRoute> { Text("Screen one") } },
    )
    private val two = FeatureEntry(
        id = "two",
        flagKey = "two",
        tab = Tab(TwoRoute, R.string.test_tab_two, Icons.Default.Star),
        routes = { composable<TwoRoute> { Text("Screen two") } },
    )

    private fun show(definition: AppDefinition, visible: List<Tab> = definition.tabs) {
        rule.setContent { AppTheme { ShellNavHost(definition = definition, visibleTabs = visible, deepLinkScheme = "test") } }
    }

    @Test
    fun oneVisibleTab_hidesBottomBar() {
        show(appDefinition { add(one) })

        rule.onNodeWithText("Screen one").assertIsDisplayed()
        rule.onNodeWithTag(BOTTOM_BAR_TAG).assertDoesNotExist()
    }

    @Test
    fun twoVisibleTabs_showBottomBarAndSwitchOnClick() {
        show(appDefinition { add(one); add(two) })

        rule.onNodeWithTag(BOTTOM_BAR_TAG).assertIsDisplayed()
        rule.onNodeWithText("Tab two").performClick()
        rule.onNodeWithText("Screen two").assertIsDisplayed()
    }

    @Test
    fun flaggedOffStartTab_startsAtFirstVisible() {
        val definition = appDefinition { add(one); add(two); startTab(TwoRoute) }

        show(definition, visible = definition.visibleTabs(mapOf("two" to false), emptySet()))

        rule.onNodeWithText("Screen one").assertIsDisplayed()
    }

    @Test
    fun noVisibleTab_showsEmptyState() {
        show(appDefinition { add(two) }, visible = emptyList())

        val title = rule.activity.getString(com.whitelabel.core.shell.R.string.shell_no_features_title)
        rule.onNodeWithText(title).assertIsDisplayed()
    }

    /** The shell pads for the system bars; a feature's own Scaffold must not pad for them again. */
    @Test
    fun featureScaffold_padsForStatusBarOnlyOnce() {
        rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
        var statusBarTop = 0
        val screen = FeatureEntry(
            id = "inset",
            tab = Tab(OneRoute, R.string.test_tab_one, Icons.Default.Home),
            routes = {
                composable<OneRoute> {
                    statusBarTop = WindowInsets.statusBars.getTop(LocalDensity.current)
                    Scaffold { inner -> Box(Modifier.padding(inner).fillMaxSize().testTag("content")) }
                }
            },
        )

        show(appDefinition { add(screen) })
        // Insets reach the window shortly after the first frame; wait for them instead of racing.
        rule.waitUntil(timeoutMillis = 5_000) { statusBarTop > 0 }
        rule.waitForIdle()

        val contentTop = rule.onNodeWithTag("content").fetchSemanticsNode().positionInRoot.y.roundToInt()
        assertThat(contentTop).isEqualTo(statusBarTop)
    }

    /** Shows two tabs under [pack] and returns the bar's own color at its top centre, plus the scheme. */
    private fun barPixel(pack: StylePack, dark: Boolean): Pair<Int, Pair<Color, Color>> {
        var surface = Color.Unspecified
        var surfaceContainer = Color.Unspecified
        rule.setContent {
            AppTheme(darkTheme = dark, stylePack = pack) {
                surface = MaterialTheme.colorScheme.surface
                surfaceContainer = MaterialTheme.colorScheme.surfaceContainer
                ShellNavHost(definition = appDefinition { add(one); add(two) }, visibleTabs = listOf(one.tab!!, two.tab!!), deepLinkScheme = "test")
            }
        }
        val image = rule.onNodeWithTag(BOTTOM_BAR_TAG).captureToImage().toPixelMap()
        // Above the items' indicators: only the bar's container is drawn there.
        return image[image.width / 2, 4].toArgb() to (surface to surfaceContainer)
    }

    @Test
    fun galleryBar_isSurfaceContainerByDefault() {
        val (pixel, scheme) = barPixel(StylePack.Gallery, dark = false)
        assertThat(pixel).isEqualTo(scheme.second.toArgb())
    }

    @Test
    fun galleryBar_isSurfaceContainerByDefault_dark() {
        val (pixel, scheme) = barPixel(StylePack.Gallery, dark = true)
        assertThat(pixel).isEqualTo(scheme.second.toArgb())
    }

    @Test
    fun galleryBarOnSurface_isSurface_light() {
        val (pixel, scheme) = barPixel(StylePack.Gallery.copy(bottomBar = BottomBarStyle.Surface), dark = false)
        assertThat(pixel).isEqualTo(scheme.first.toArgb())
    }

    @Test
    fun galleryBarOnSurface_isSurface_dark() {
        val (pixel, scheme) = barPixel(StylePack.Gallery.copy(bottomBar = BottomBarStyle.Surface), dark = true)
        assertThat(pixel).isEqualTo(scheme.first.toArgb())
    }

    /** A Shadow pack has no hairline to separate a surface bar, so it ignores [BottomBarStyle.Surface]. */
    @Test
    fun materialBar_ignoresSurfaceBarStyle() {
        val (pixel, scheme) = barPixel(StylePack.Material.copy(bottomBar = BottomBarStyle.Surface), dark = false)
        assertThat(pixel).isEqualTo(scheme.second.toArgb())
    }

    /** Tend's four tabs, the first selected. */
    private val four = listOf(
        FeatureEntry(id = "today", tab = Tab(OneRoute, R.string.test_tab_today, Icons.Default.Home), routes = { composable<OneRoute> { Text("Screen one") } }),
        FeatureEntry(id = "calendar", tab = Tab(TwoRoute, R.string.test_tab_calendar, Icons.Default.Star), routes = { composable<TwoRoute> { Text("Screen two") } }),
        FeatureEntry(id = "garden", tab = Tab(ThreeRoute, R.string.test_tab_garden, Icons.Default.Star), routes = { composable<ThreeRoute> { Text("Screen three") } }),
        FeatureEntry(id = "settings", tab = Tab(FourRoute, R.string.test_tab_settings, Icons.Default.Star), routes = { composable<FourRoute> { Text("Screen four") } }),
    )

    private lateinit var scheme: androidx.compose.material3.ColorScheme
    private lateinit var labelStyle: androidx.compose.ui.text.TextStyle
    private lateinit var measurer: androidx.compose.ui.text.TextMeasurer

    private fun showFour(pack: StylePack, fontScale: Float = 1f, entries: List<FeatureEntry> = four) {
        rule.setContent {
            val density = LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(LocalDensity provides androidx.compose.ui.unit.Density(density.density, fontScale)) {
                AppTheme(darkTheme = false, stylePack = pack) {
                    scheme = MaterialTheme.colorScheme
                    labelStyle = MaterialTheme.typography.labelMedium
                    measurer = androidx.compose.ui.text.rememberTextMeasurer()
                    val definition = appDefinition { entries.forEach { add(it) } }
                    ShellNavHost(definition = definition, visibleTabs = definition.tabs, deepLinkScheme = "test")
                }
            }
        }
        rule.waitForIdle()
    }

    private fun label(text: String): androidx.compose.ui.text.TextLayoutResult {
        val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        rule.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.first()
    }

    private fun assertLabelsOnOneLine() {
        for (text in listOf("Today", "Calendar", "Garden", "Settings")) {
            val layout = label(text)
            assertThat(layout.lineCount).isEqualTo(1)
            assertThat(layout.isLineEllipsized(0)).isEqualTo(false)
        }
    }

    /** Bug 8zZ4HDYM: at 200 % every label stays on one line; none breaks mid-word ("Calenda/r"). */
    @Test
    fun fourTabsAt200Percent_labelsStayOnOneLine_gallery() {
        showFour(StylePack.Gallery, fontScale = 2f)
        assertLabelsOnOneLine()
    }

    @Test
    fun fourTabsAt200Percent_labelsStayOnOneLine_material() {
        showFour(StylePack.Material, fontScale = 2f)
        assertLabelsOnOneLine()
    }

    /**
     * [text] laid out on one line at labelMedium's full size under the test's font scale. The
     * semantics layout reports the label's style before auto-sizing, so sizes are compared by the
     * rendered line's width, not by its reported font size.
     */
    private fun fullSize(text: String) = rule.runOnIdle { measurer.measure(text, labelStyle, softWrap = false) }

    private fun lineWidth(layout: androidx.compose.ui.text.TextLayoutResult, line: Int = 0) =
        layout.getLineRight(line) - layout.getLineLeft(line)

    /** Short labels that fit at 200 % keep the user's full size: nothing caps them. */
    @Test
    fun threeTabsAt200Percent_labelsKeepTheirFullSize() {
        showFour(StylePack.Gallery, fontScale = 2f, entries = four.take(3))
        for (text in listOf("Today", "Calendar", "Garden")) {
            val layout = label(text)
            assertThat(layout.lineCount).isEqualTo(1)
            assertThat(lineWidth(layout)).isCloseTo(lineWidth(fullSize(text)), 1f)
        }
    }

    /** At 200 % four tabs shrink "Calendar" only as far as it needs: below full size, above its 100 % size. */
    @Test
    fun fourTabsAt200Percent_labelsShrinkNoFurtherThanTheirDefaultSize() {
        showFour(StylePack.Gallery, fontScale = 2f)
        val width = lineWidth(label("Calendar"))
        val full = lineWidth(fullSize("Calendar"))
        assertThat(width).isLessThan(full)
        assertThat(width).isGreaterThanOrEqualTo(full / 2f)
    }

    /** A label too long for its tab at 100 % wraps between its words as before: no ellipsis, no shrink. */
    @Test
    fun longLabelAtDefaultScale_wrapsBetweenWordsAsBefore() {
        val long = FeatureEntry(id = "long", tab = Tab(FourRoute, R.string.test_tab_long, Icons.Default.Star), routes = { composable<FourRoute> { Text("Screen four") } })
        showFour(StylePack.Gallery, entries = four.take(3) + long)
        val layout = label("Notification preferences")
        assertThat(layout.lineCount).isEqualTo(2)
        assertThat(layout.getLineEnd(0)).isEqualTo("Notification ".length)
        assertThat(layout.isLineEllipsized(1)).isEqualTo(false)
        assertThat(lineWidth(layout, line = 1)).isCloseTo(lineWidth(fullSize("preferences")), 1f)
    }

    /**
     * A shrunk label's line height shrinks with it, keeping labelMedium's height-to-width ratio, so
     * the bar doesn't keep the full size's empty line space.
     */
    @Test
    fun shrunkLabel_lineHeightFollowsItsSize() {
        showFour(StylePack.Gallery, fontScale = 2f)
        val layout = label("Calendar")
        val full = fullSize("Calendar")
        val ratio = (layout.getLineBottom(0) - layout.getLineTop(0)) / lineWidth(layout)
        val fullRatio = (full.getLineBottom(0) - full.getLineTop(0)) / lineWidth(full)
        assertThat(ratio).isCloseTo(fullRatio, fullRatio * 0.05f)
    }

    @Test
    fun squaredItems_selectedLabelIsOnSurface() {
        showFour(StylePack.Gallery.copy(bottomBarItems = BottomBarItemStyle.Squared))
        assertThat(label("Today").layoutInput.style.color).isEqualTo(scheme.onSurface)
    }

    @Test
    fun materialItems_keepMaterialsSelectedLabel() {
        showFour(StylePack.Gallery)
        assertThat(label("Today").layoutInput.style.color == scheme.onSurface).isEqualTo(false)
    }

    /**
     * The 56 x 32 indicator around the selected icon: its top-left corner pixel (3 px in) is filled
     * with a 4 dp corner, and left empty by a pill.
     */
    private fun indicatorCornerFilled(): Boolean {
        val icon = rule.onNodeWithTag(TAB_INDICATOR_TAG, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val root = rule.onRoot().captureToImage().asAndroidBitmap()
        val px = rule.activity.resources.displayMetrics.density
        val left = (icon.center.x - 28 * px).roundToInt()
        val top = (icon.center.y - 16 * px).roundToInt()
        val corner = root.getPixel(left + 3, top + 3)
        val edge = root.getPixel(icon.center.x.roundToInt(), top + 3)
        return corner == edge && Color(corner) == scheme.secondaryContainer
    }

    @Test
    fun squaredItems_indicatorHasSmallCorners() {
        showFour(StylePack.Gallery.copy(bottomBarItems = BottomBarItemStyle.Squared))
        assertThat(indicatorCornerFilled()).isEqualTo(true)
    }

    /**
     * Holds a press on the unselected Calendar tab and returns its indicator's top-left corner
     * pixel (6 px in) and the bar's colour beside the indicator. A pill's ripple can't reach that
     * corner, a squared one fills it.
     */
    private fun pressedCornerAndBar(): Pair<Int, Int> {
        val icon = rule.onNodeWithText("Calendar", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val item = rule.onNodeWithText("Calendar").fetchSemanticsNode().boundsInRoot
        val px = rule.activity.resources.displayMetrics.density
        rule.onNodeWithText("Calendar").performTouchInput { down(center) }
        // Android's ripple is a RippleDrawable the render thread animates in real time.
        Thread.sleep(600)
        val root = rule.onRoot().captureToImage().asAndroidBitmap()
        // The indicator is centred on the item, 32 dp tall, ending 4 dp above the label.
        val left = (item.center.x - 28 * px).roundToInt()
        val top = (icon.top - 4 * px - 32 * px).roundToInt()
        val corner = root.getPixel(left + 6, top + 6)
        val bar = root.getPixel(left - 4, top + 6)
        rule.onNodeWithText("Calendar").performTouchInput { up() }
        return corner to bar
    }

    @Test
    fun squaredItems_pressRippleFillsTheSquareCorners() {
        showFour(StylePack.Gallery.copy(bottomBarItems = BottomBarItemStyle.Squared))
        val (corner, bar) = pressedCornerAndBar()
        assertThat(corner == bar).isEqualTo(false)
    }

    /** No tag is drawn under Material's items, so the squared indicator can't be there. */
    @Test
    fun materialItems_haveNoSquaredIndicator() {
        showFour(StylePack.Gallery)
        rule.onNodeWithTag(TAB_INDICATOR_TAG, useUnmergedTree = true).assertDoesNotExist()
    }
}
