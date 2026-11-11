package com.whitelabel.core.shell

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.whitelabel.core.designsystem.motion.sharedElementTransition
import com.whitelabel.core.designsystem.theme.AppTheme
import com.whitelabel.core.designsystem.theme.StylePack
import com.whitelabel.core.shell.test.R
import kotlinx.serialization.Serializable
import org.junit.Rule
import org.junit.Test

@Serializable data object ListRoute
@Serializable data object DetailRoute

/** A shared element between a tab and a pushed screen animates both ways without crashing. */
class SharedElementTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun definition() = appDefinition {
        add(
            FeatureEntry(
                id = "list",
                tab = Tab(ListRoute, R.string.test_tab_one, Icons.Default.Home),
                routes = { scope ->
                    screen<ListRoute> {
                        Column {
                            Box(Modifier.size(40.dp).sharedElementTransition("hero").testTag("listHero"))
                            Text("Open", Modifier.clickable { scope.navigator.navigate(DetailRoute) })
                        }
                    }
                    screen<DetailRoute> {
                        Column {
                            Box(Modifier.size(120.dp).sharedElementTransition("hero").testTag("detailHero"))
                            Text("Back", Modifier.clickable { scope.navigator.back() })
                        }
                    }
                },
            ),
        )
    }

    private fun pushAndPop(pack: StylePack) {
        val definition = definition()
        rule.setContent {
            AppTheme(stylePack = pack) { ShellNavHost(definition = definition, visibleTabs = definition.tabs, deepLinkScheme = "test") }
        }
        rule.onNodeWithTag("listHero").assertIsDisplayed()
        rule.onNodeWithText("Open").performClick()
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        rule.onNodeWithTag("detailHero").assertIsDisplayed()
        rule.onNodeWithText("Back").performClick()
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        rule.onNodeWithTag("listHero").assertIsDisplayed()
    }

    @Test fun materialPushAndPop() = pushAndPop(StylePack.Material)

    @Test fun galleryPushAndPop() = pushAndPop(StylePack.Gallery)
}
