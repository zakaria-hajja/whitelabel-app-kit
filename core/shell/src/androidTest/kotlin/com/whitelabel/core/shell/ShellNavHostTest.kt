package com.whitelabel.core.shell

import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.composable
import com.whitelabel.core.designsystem.theme.AppTheme
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
        rule.setContent { AppTheme { ShellNavHost(definition = definition, visibleTabs = visible) } }
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
}
