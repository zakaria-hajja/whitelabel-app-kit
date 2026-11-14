package com.whitelabel.core.shell

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import com.whitelabel.core.designsystem.theme.AppTheme
import com.whitelabel.core.shell.model.ShellScreen
import com.whitelabel.core.shell.test.R
import kotlinx.serialization.Serializable
import org.junit.Rule
import org.junit.Test

@Serializable data object LockHomeRoute
@Serializable data object LockOtherRoute

/** [ShellRoot] around the real [ShellNavHost]: the lock replaces the content, which keeps its state. */
class ContentGateTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val home = FeatureEntry(
        id = "home",
        tab = Tab(LockHomeRoute, R.string.test_tab_one, Icons.Default.Home),
        routes = {
            composable<LockHomeRoute> {
                var dialog by remember { mutableStateOf(false) }
                Column {
                    Text("Home screen")
                    Text("Open dialog", Modifier.clickable { dialog = true })
                }
                if (dialog) Dialog(onDismissRequest = { dialog = false }) { Text("Dialog text") }
            }
        },
    )
    private val other = FeatureEntry(
        id = "other",
        tab = Tab(LockOtherRoute, R.string.test_tab_two, Icons.Default.Star),
        routes = {
            composable<LockOtherRoute>(deepLinks = listOf(navDeepLink { uriPattern = "test://other" })) {
                var taps by rememberSaveable { mutableIntStateOf(0) }
                Column {
                    Text("Other screen")
                    Text("Taps $taps", Modifier.clickable { taps++ })
                }
            }
        },
    )
    private val definition = appDefinition {
        add(home)
        add(other)
    }

    private var screen by mutableStateOf(ShellScreen.CONTENT)
    private var deepLink by mutableStateOf<String?>(null)
    private var consumed = 0
    private var shown = 0
    private var contentCompositions = 0
    private val screenViews = mutableListOf<String>()
    private lateinit var nav: NavHostController

    @androidx.compose.runtime.Composable
    private fun Gated() {
        nav = rememberNavController()
        AppTheme {
            ShellRoot(screen, lockScreen = { Text("Lock screen") }, onContentShown = { shown++ }) {
                SideEffect { contentCompositions++ }
                ShellNavHost(
                    definition = definition,
                    visibleTabs = definition.tabs,
                    deepLinkScheme = "test",
                    deepLink = deepLink,
                    onDeepLinkConsumed = { consumed++; deepLink = null },
                    navController = nav,
                    onScreenView = { screenViews += it },
                )
            }
        }
    }

    private fun show() = rule.setContent { Gated() }

    private fun lock() {
        screen = ShellScreen.LOCK
        rule.waitForIdle()
    }

    private fun unlock() {
        screen = ShellScreen.CONTENT
        rule.waitForIdle()
    }

    @Test
    fun locked_composesOnlyTheLockScreen() {
        screen = ShellScreen.LOCK
        show()
        rule.onNodeWithTag(LOCK_SCREEN_TAG).assertExists()
        rule.onNodeWithText("Lock screen").assertExists()
        rule.onNodeWithText("Home screen", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag(BOTTOM_BAR_TAG, useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithText("Tab one", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithText("Tab two", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun locked_composesNothingOfTheContent() {
        screen = ShellScreen.SPLASH
        show()
        rule.runOnIdle { assertThat(contentCompositions).isEqualTo(0) }
        lock()
        rule.runOnIdle { assertThat(contentCompositions).isEqualTo(0) }
        unlock()
        rule.onNodeWithText("Home screen").assertExists()
        rule.runOnIdle { assertThat(contentCompositions).isGreaterThan(0) }
    }

    @Test
    fun deepLinkWhileLocked_opensOnlyAfterUnlock() {
        screen = ShellScreen.LOCK
        show()
        deepLink = "test://other"
        rule.waitForIdle()
        rule.onNodeWithText("Other screen", useUnmergedTree = true).assertDoesNotExist()
        rule.runOnIdle { assertThat(consumed).isEqualTo(0) }
        unlock()
        rule.onNodeWithText("Other screen").assertExists()
        rule.runOnIdle { assertThat(consumed).isEqualTo(1) }
    }

    @Test
    fun relock_removesOpenDialog() {
        show()
        rule.onNodeWithText("Open dialog").performClick()
        rule.onNodeWithText("Dialog text").assertExists()
        lock()
        rule.onNodeWithText("Dialog text", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithText("Lock screen").assertExists()
    }

    @Test
    fun unlock_returnsToSameDestinationWithState() {
        show()
        rule.onNodeWithText("Tab two").performClick()
        rule.onNodeWithText("Taps 0").performClick()
        rule.onNodeWithText("Taps 1").performClick()
        rule.onNodeWithText("Taps 2").assertExists()
        var previousId: String? = null
        var viewsBefore = 0
        rule.runOnIdle {
            previousId = nav.previousBackStackEntry?.id
            viewsBefore = screenViews.size
        }
        lock()
        rule.onNodeWithText("Other screen", useUnmergedTree = true).assertDoesNotExist()
        unlock()
        rule.onNodeWithText("Other screen").assertExists()
        rule.onNodeWithText("Taps 2").assertExists()
        rule.runOnIdle {
            assertThat(nav.previousBackStackEntry?.id).isEqualTo(previousId)
            assertThat(screenViews.size).isEqualTo(viewsBefore + 1)
            assertThat(screenViews.last()).isEqualTo(screenViews[viewsBefore - 1])
        }
    }

    @Test
    fun processDeathWhileLocked_restoresDestinationAfterUnlock() {
        val tester = StateRestorationTester(rule)
        tester.setContent { Gated() }
        rule.onNodeWithText("Tab two").performClick()
        rule.onNodeWithText("Taps 0").performClick()
        rule.onNodeWithText("Taps 1").assertExists()
        lock()
        tester.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("Lock screen").assertExists()
        rule.onNodeWithText("Other screen", useUnmergedTree = true).assertDoesNotExist()
        unlock()
        rule.onNodeWithText("Other screen").assertExists()
        rule.onNodeWithText("Taps 1").assertExists()
    }

    @Test
    fun onContentShown_firesOnEachEntryIntoContent_neverWhileLocked() {
        screen = ShellScreen.LOCK
        show()
        rule.runOnIdle { assertThat(shown).isEqualTo(0) }
        unlock()
        rule.runOnIdle { assertThat(shown).isEqualTo(1) }
        lock()
        rule.runOnIdle { assertThat(shown).isEqualTo(1) }
        unlock()
        rule.runOnIdle { assertThat(shown).isEqualTo(2) }
    }

    @Test
    fun gatedRoot_carriesTheRootTag() {
        show()
        rule.onNodeWithTag(CONTENT_GATE_ROOT_TAG).assertExists()
        // Test 9 (Showcase, no gate) asserts this string's absence as a literal.
        assertThat(CONTENT_GATE_ROOT_TAG).isEqualTo("ShellContentGateRoot")
    }
}
