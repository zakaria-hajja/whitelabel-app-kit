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
import assertk.assertions.isEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.composable
import com.whitelabel.core.designsystem.theme.AppTheme
import com.whitelabel.core.designsystem.theme.BottomBarStyle
import com.whitelabel.core.designsystem.theme.StylePack
import com.whitelabel.core.shell.test.R
import kotlinx.serialization.Serializable
import org.junit.Rule
import org.junit.Test

@Serializable data object OneRoute
@Serializable data object TwoRoute

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
}
