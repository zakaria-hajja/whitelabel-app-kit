package com.whitelabel.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import assertk.assertThat
import assertk.assertions.containsExactly
import com.whitelabel.core.designsystem.theme.AppTheme
import com.whitelabel.feature.home.presentation.HomeScreen
import com.whitelabel.feature.home.presentation.HomeState
import com.whitelabel.feature.home.test.R
import org.junit.Rule
import org.junit.Test

private object ShownTab
private object HiddenTab

class HomeScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val opened = mutableListOf<Any>()

    @Test
    fun hiddenTabShortcut_isNotShown_andAShownOneOpensItsTab() {
        val options = HomeOptions(
            shortcuts = listOf(
                HomeShortcut(R.string.test_shortcut_shown, Icons.Default.Person, ShownTab),
                HomeShortcut(R.string.test_shortcut_hidden, Icons.Default.Settings, HiddenTab),
            ),
        )
        rule.setContent {
            AppTheme {
                HomeScreen(
                    state = HomeState(),
                    options = options,
                    canOpenTab = { it == ShownTab },
                    onOpenTab = { opened += it },
                    onAction = {},
                )
            }
        }
        rule.onNodeWithText("Hidden shortcut").assertDoesNotExist()
        rule.onNodeWithText("Shown shortcut").performClick()
        rule.runOnIdle { assertThat(opened).containsExactly(ShownTab) }
    }
}
