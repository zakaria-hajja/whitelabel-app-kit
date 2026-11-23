package com.whitelabel.core.designsystem

import androidx.activity.ComponentActivity
import android.os.ParcelFileDescriptor
import android.view.View
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.remember
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.whitelabel.core.designsystem.component.AppTextField
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThan
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isTrue
import com.whitelabel.core.designsystem.component.ActionScaffoldAlignment
import com.whitelabel.core.designsystem.component.AppActionScaffold
import com.whitelabel.core.designsystem.component.AppPrimaryButton
import com.whitelabel.core.designsystem.component.AppTextButton
import com.whitelabel.core.designsystem.theme.AppTheme
import com.whitelabel.core.designsystem.theme.StylePack
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

private val StatusBar = 24.dp
private val NavBar = 48.dp

class AppActionScaffoldTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val inDock = hasAnyAncestor(hasTestTag("AppActionDock"))
    private val inContent = hasAnyAncestor(hasTestTag("AppActionScaffoldContent"))

    /**
     * The scaffold gets exactly [width] x [height], hosted as every app hosts it: in the shell,
     * which pads for the system bars and consumes them (`ShellNavHost`). The window insets are
     * fixed, so the thresholds don't depend on the device: a 24 status bar, a 48 three-button
     * navigation bar and, when [keyboard] > 0, a keyboard of that height (measured, like the nav
     * bar, from the bottom of the window).
     */
    private fun show(
        width: Dp = 360.dp,
        height: Dp = 640.dp,
        fontScale: Float = 1f,
        keyboard: Dp = 0.dp,
        content: @Composable () -> Unit,
    ) {
        lateinit var view: View
        var seenNavBar = -1
        var seenKeyboard = -1
        rule.setContent {
            view = LocalView.current
            val density = LocalDensity.current
            seenNavBar = WindowInsets.navigationBars.getBottom(density)
            seenKeyboard = WindowInsets.ime.getBottom(density)
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                AppTheme(stylePack = StylePack.Gallery) {
                    Box(Modifier.size(width, height + StatusBar + NavBar)) {
                        val bars = WindowInsets.systemBars.asPaddingValues()
                        Box(Modifier.fillMaxSize().padding(bars).consumeWindowInsets(bars)) { content() }
                    }
                }
            }
        }
        fun px(dp: Dp) = with(rule.density) { dp.roundToPx() }
        rule.runOnIdle {
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, px(StatusBar), 0, 0))
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, px(NavBar)))
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, px(keyboard)))
                .setVisible(WindowInsetsCompat.Type.ime(), keyboard > 0.dp)
                .build()
            ViewCompat.dispatchApplyWindowInsets(view, insets)
        }
        rule.waitForIdle()
        // Fail loudly if the system replaced the fixed insets with the device's own.
        assertThat(seenNavBar).isEqualTo(px(NavBar))
        assertThat(seenKeyboard).isEqualTo(px(keyboard))
    }

    @Composable
    private fun Rows(count: Int) {
        repeat(count) { Text("Row $it", Modifier.fillMaxWidth().height(48.dp).testTag("row$it")) }
    }

    private fun bounds(tag: String) = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    @Test
    fun dockIsPinnedAtTheBottom() {
        show {
            AppActionScaffold(primary = { AppPrimaryButton("Continue", {}, Modifier.testTag("primary")) }) { Rows(2) }
        }
        rule.onNodeWithTag("primary").assertIsDisplayed()
        assertThat(bounds("AppActionDock").bottom).isEqualTo(bounds("AppActionScaffold").bottom)
    }

    @Test
    fun fadeShowsOnlyWhileContentCanScroll() {
        val scroll = ScrollState(0)
        var rows by mutableStateOf(40)
        show {
            AppActionScaffold(primary = { AppPrimaryButton("Continue", {}) }, scrollState = scroll) { Rows(rows) }
        }
        rule.onNodeWithTag("AppActionDockFade").assertExists()

        runBlocking { scroll.scrollTo(scroll.maxValue) }
        rule.mainClock.advanceTimeBy(200)
        rule.waitForIdle()
        rule.onNodeWithTag("AppActionDockFade").assertDoesNotExist()

        rows = 2
        rule.mainClock.advanceTimeBy(200)
        rule.waitForIdle()
        rule.onNodeWithTag("AppActionDockFade").assertDoesNotExist()
    }

    @Test
    fun shortContentNeverShowsTheFade() {
        show { AppActionScaffold(primary = { AppPrimaryButton("Continue", {}) }) { Rows(2) } }
        rule.onNodeWithTag("AppActionDockFade").assertDoesNotExist()
    }

    @Test
    fun primaryStaysPinnedAt200Percent() {
        show(fontScale = 2f) {
            AppActionScaffold(
                primary = { AppPrimaryButton("Continue to the next step", {}, Modifier.testTag("primary")) },
                secondary = { AppTextButton("I don't remember", {}) },
                finePrint = { Text("Everything stays on this phone. Nothing is shared, ever, with anyone.") },
            ) { Rows(40) }
        }
        rule.onNodeWithTag("primary").assert(inDock).assertIsDisplayed()
        assertThat(bounds("primary").bottom).isLessThanOrEqualTo(bounds("AppActionScaffold").bottom)
    }

    @Composable
    private fun FixedDock(content: @Composable () -> Unit = { Rows(3) }) {
        AppActionScaffold(
            primary = { Box(Modifier.fillMaxWidth().height(56.dp).testTag("primary")) },
            secondary = { Box(Modifier.fillMaxWidth().height(64.dp).testTag("secondary")) },
            finePrint = { Box(Modifier.fillMaxWidth().height(100.dp).testTag("fine")) },
        ) { content() }
    }

    @Test
    fun overflowMovesFinePrintThenSecondary() {
        // Dock chrome 12 + 16 (the shell already padded for the nav bar), items 8 apart: full dock
        // 264, without fine print 156. 480 high: cap 192, so only the fine print moves.
        show(height = 480.dp, fontScale = 2f) { FixedDock() }
        rule.onNodeWithTag("fine").assert(inContent)
        rule.onNodeWithTag("secondary").assert(inDock)
        rule.onNodeWithTag("primary").assert(inDock)
    }

    @Test
    fun overflowMovesTheSecondaryWhenStillTooTall() {
        // 360 high: cap 144 < 156.
        show(height = 360.dp, fontScale = 2f) { FixedDock() }
        rule.onNodeWithTag("fine").assert(inContent)
        rule.onNodeWithTag("secondary").assert(inContent)
        rule.onNodeWithTag("primary").assert(inDock)
        // Same order as in the dock: secondary, then fine print, after the content.
        assertThat(bounds("row2").bottom).isLessThanOrEqualTo(bounds("secondary").top)
        assertThat(bounds("secondary").bottom).isLessThanOrEqualTo(bounds("fine").top)
    }

    @Test
    fun aTallWindowKeepsEverythingInTheDock() {
        show(height = 900.dp) { FixedDock() }
        rule.onNodeWithTag("fine").assert(inDock)
        rule.onNodeWithTag("secondary").assert(inDock)
        assertThat(bounds("secondary").bottom).isLessThanOrEqualTo(bounds("fine").top)
        assertThat(bounds("primary").bottom).isLessThanOrEqualTo(bounds("secondary").top)
    }

    @Test
    fun centerAlignmentCentresShortContent() {
        show {
            AppActionScaffold(
                primary = { AppPrimaryButton("Continue", {}) },
                contentAlignment = ActionScaffoldAlignment.Center,
            ) { Box(Modifier.size(100.dp).testTag("hero")) }
        }
        val viewport = bounds("AppActionScaffoldContent")
        val hero = bounds("hero")
        val viewportCentre = (viewport.top + viewport.bottom) / 2
        val heroCentre = (hero.top + hero.bottom) / 2
        assertThat(heroCentre.value).isCloseTo(viewportCentre.value, 1f)
        rule.onNodeWithTag("AppActionDockFade").assertDoesNotExist()
    }

    @Test
    fun snackbarSitsAboveTheDock() {
        show {
            AppActionScaffold(
                primary = { AppPrimaryButton("Continue", {}) },
                snackbarHost = { Snackbar(Modifier.testTag("snackbar")) { Text("Removed 13 Oct") } },
            ) { Rows(40) }
        }
        assertThat(bounds("snackbar").bottom).isLessThanOrEqualTo(bounds("AppActionDock").top - 8.dp)
    }

    @Test
    fun dockHeightIsStableWhilePrimaryLoads() {
        var loading by mutableStateOf(false)
        show {
            AppActionScaffold(primary = { AppPrimaryButton("Save", {}, loading = loading) }) { Rows(2) }
        }
        val before = bounds("AppActionDock")
        loading = true
        rule.waitForIdle()
        rule.onNodeWithTag("AppButtonProgress").assertExists()
        val after = bounds("AppActionDock")
        assertThat(after.top).isEqualTo(before.top)
        assertThat(after.bottom).isEqualTo(before.bottom)
    }

    @Test
    fun dockIsLastInTraversal() {
        show {
            AppActionScaffold(
                primary = { AppPrimaryButton("Continue", {}) },
                topBar = { Text("Title", Modifier.testTag("top")) },
            ) { Rows(40) }
        }
        val tags = rule.onNodeWithTag("AppActionScaffold").fetchSemanticsNode().children
            .map { it.config.getOrElse(SemanticsProperties.TestTag) { "" } }
        assertThat(tags.last()).isEqualTo("AppActionDock")
        assertThat(tags.indexOf("top")).isLessThan(tags.indexOf("AppActionScaffoldContent"))
        assertThat(tags.indexOf("AppActionScaffoldContent") >= 0).isTrue()
    }

    // A slot's content changing while the screen shows (the slot itself stays, as with
    // `message = { ui.error?.let { ErrorLine(it) } }`).

    @Composable
    private fun MessageDock(message: Dp?, withMessage: Boolean = true) {
        AppActionScaffold(
            primary = { Box(Modifier.fillMaxWidth().height(56.dp).testTag("primary")) },
            message = if (withMessage) ({ if (message != null) Box(Modifier.fillMaxWidth().height(message)) }) else null,
            finePrint = { Box(Modifier.fillMaxWidth().height(40.dp).testTag("fine")) },
        ) { Rows(2) }
    }

    /** 480 high, cap 192: a 200 message moves the fine print; with it shrunk or gone, the dock is 132 or less. */
    private fun assertFinePrintReturns(shrunk: Dp?) {
        var message by mutableStateOf<Dp?>(200.dp)
        show(height = 480.dp) { MessageDock(message) }
        rule.onNodeWithTag("fine").assert(inContent)
        message = shrunk
        rule.waitForIdle()
        rule.onNodeWithTag("fine").assert(inDock)
    }

    @Test
    fun extrasReturnToTheDockWhenTheMessageDisappears() = assertFinePrintReturns(shrunk = null)

    @Test
    fun extrasReturnToTheDockWhenTheMessageShrinks() = assertFinePrintReturns(shrunk = 10.dp)

    @Test
    fun anEmptyMessageAddsNoGap() {
        var withMessage by mutableStateOf(false)
        show { MessageDock(message = null, withMessage = withMessage) }
        // The dock is pinned at the bottom, so a stray gap would raise its top.
        val none = bounds("AppActionDock").top
        withMessage = true
        rule.waitForIdle()
        assertThat(bounds("AppActionDock").top).isEqualTo(none)
    }

    // A keyboard over the shell's consumed nav bar: 300 from the window's bottom, so 252 below the
    // scaffold. Extras are fixed: dock 12 + 56 + 8 + 20 + 8 + 20 + 16 = 140.

    @Composable
    private fun SmallDock() {
        AppActionScaffold(
            primary = { Box(Modifier.fillMaxWidth().height(56.dp).testTag("primary")) },
            secondary = { Box(Modifier.size(120.dp, 20.dp).testTag("secondary")) },
            finePrint = { Box(Modifier.size(120.dp, 20.dp).testTag("fine")) },
        ) { Rows(3) }
    }

    @Test
    fun theDockSitsOnAKeyboardOverAConsumedNavBar() {
        show(height = 617.dp, keyboard = 300.dp) { SmallDock() }
        // The dock's background runs to the edge; its parts sit on the keyboard.
        assertThat(bounds("AppActionDock").bottom).isEqualTo(bounds("AppActionScaffold").bottom)
        assertThat(bounds("primary").bottom).isLessThanOrEqualTo(bounds("AppActionScaffold").bottom - 252.dp)
    }

    @Test
    fun aConsumedNavBarIsNotCountedTwiceAboveTheKeyboard() {
        // 617 - 252 = 365 left above the keyboard: over 360, and 140 fits in 40 % of it (146).
        show(height = 617.dp, keyboard = 300.dp) { SmallDock() }
        rule.onNodeWithTag("fine").assert(inDock)
        rule.onNodeWithTag("secondary").assert(inDock)
    }

    @Test
    fun underThreeSixtyAboveAKeyboardOnlyThePrimaryStays() {
        // 607 - 252 = 355 left: under 360, though 140 fits in 40 % of it (142).
        show(height = 607.dp, keyboard = 300.dp) { SmallDock() }
        rule.onNodeWithTag("fine").assert(inContent)
        rule.onNodeWithTag("secondary").assert(inContent)
        rule.onNodeWithTag("primary").assert(inDock)
    }

    // Keyboard. The team AVD has a hardware keyboard, so the soft keyboard is forced on for these
    // tests and the device's own setting is put back after. The activity is edge to edge and
    // resizes for the IME, as apps are.
    private fun withSoftKeyboard(block: () -> Unit) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        // Reading the output to its end waits for the command to finish.
        fun shell(command: String): String =
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command))
                .bufferedReader()
                .use { it.readText().trim() }
        val setting = "secure show_ime_with_hard_keyboard"
        val previous = shell("settings get $setting")
        shell("settings put $setting 1")
        try {
            block()
        } finally {
            if (previous.isEmpty() || previous == "null") shell("settings delete $setting") else shell("settings put $setting $previous")
        }
    }

    private var imeBottom = 0

    /**
     * Extras are fixed 20 dp boxes, so only the keyboard decides where they go. [aboveKeyboard] sizes
     * the window so that exactly this much is left between the status bar and the keyboard while it
     * is up; without the keyboard, or when null, it fills the window.
     */
    private fun showWithField(aboveKeyboard: Dp?) {
        rule.runOnUiThread {
            rule.activity.enableEdgeToEdge()
            rule.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        rule.setContent {
            val density = LocalDensity.current
            imeBottom = WindowInsets.ime.getBottom(density)
            AppTheme(stylePack = StylePack.Gallery) {
                // The scaffold pads for the status bar itself, so it is added to the window too.
                val top = WindowInsets.safeDrawing.getTop(density)
                val window = aboveKeyboard?.takeIf { imeBottom > 0 }?.let { with(density) { it + (top + imeBottom).toDp() } }
                Box(if (window == null) Modifier.fillMaxSize() else Modifier.fillMaxWidth().height(window)) {
                    AppActionScaffold(
                        primary = { AppPrimaryButton("Continue", {}, Modifier.testTag("primary")) },
                        secondary = { Box(Modifier.size(120.dp, 20.dp).testTag("secondary")) },
                        finePrint = { Box(Modifier.size(120.dp, 20.dp).testTag("fine")) },
                    ) {
                        var text by remember { mutableStateOf("") }
                        AppTextField(text, { text = it }, "Name", Modifier.testTag("field"))
                    }
                }
            }
        }
    }

    private fun openKeyboard() {
        rule.onNodeWithTag("field").performClick()
        rule.waitUntil(5_000) { imeBottom > 0 }
        rule.waitForIdle()
    }

    @Test
    fun dockRidesTheKeyboard() = withSoftKeyboard {
        showWithField(aboveKeyboard = null)
        openKeyboard()
        val keyboardTop = with(rule.density) { (rule.onRoot().getUnclippedBoundsInRoot().bottom.toPx() - imeBottom).toDp() }
        rule.onNodeWithTag("primary").assertIsDisplayed()
        assertThat(bounds("primary").bottom).isLessThanOrEqualTo(keyboardTop)
    }

    @Test
    fun aTallWindowKeepsExtrasInTheDockWithTheKeyboardUp() = withSoftKeyboard {
        // The whole window: well over 360 stays above the keyboard and the small dock fits in 40 % of it.
        showWithField(aboveKeyboard = null)
        openKeyboard()
        rule.onNodeWithTag("fine").assert(inDock)
        rule.onNodeWithTag("secondary").assert(inDock)
    }

    @Test
    fun compactKeyboardMovesExtrasIntoContent() = withSoftKeyboard {
        // 355 left above the keyboard: under 360, though the dock (12 + 52 + 8 + 20 + 8 + 20 + 16 =
        // 136) fits in 40 % of it (142), so only the keyboard rule moves the extras.
        showWithField(aboveKeyboard = 355.dp)
        rule.onNodeWithTag("fine").assert(inDock)
        rule.onNodeWithTag("secondary").assert(inDock)
        openKeyboard()
        rule.onNodeWithTag("fine").assert(inContent)
        rule.onNodeWithTag("secondary").assert(inContent)
        rule.onNodeWithTag("primary").assert(inDock)

        Espresso.closeSoftKeyboard()
        rule.waitUntil(5_000) { imeBottom == 0 }
        rule.waitForIdle()
        rule.onNodeWithTag("fine").assert(inDock)
        rule.onNodeWithTag("secondary").assert(inDock)
    }
}
