package com.whitelabel.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import assertk.assertThat
import assertk.assertions.containsExactly
import com.whitelabel.core.designsystem.theme.AppTheme
import com.whitelabel.core.domain.ThemePreference
import com.whitelabel.feature.settings.presentation.SettingsAction
import com.whitelabel.feature.settings.presentation.SettingsScreen
import com.whitelabel.feature.settings.presentation.SettingsState
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val actions = mutableListOf<SettingsAction>()

    private fun show(state: SettingsState) {
        rule.setContent {
            AppTheme {
                SettingsScreen(state, SettingsOptions(), onAction = { actions += it }, onOpenUrl = {}, onOpenDiagnostics = {})
            }
        }
    }

    @Test
    fun selectDarkTheme_sendsThemeAction() {
        show(SettingsState(appName = "Sample"))
        rule.onNodeWithText("Dark").performClick()
        rule.runOnIdle { assertThat(actions).containsExactly(SettingsAction.OnThemeSelect(ThemePreference.DARK)) }
    }

    @Test
    fun signOut_confirmDialog_thenAction() {
        show(SettingsState(appName = "Sample", signedIn = true, confirmSignOut = true))
        rule.onNodeWithText("Sign out?").assertExists()
        rule.onNodeWithText("Cancel").performClick()
        rule.runOnIdle { assertThat(actions).containsExactly(SettingsAction.OnSignOutDismiss) }
    }

    @Test
    fun signedOut_hidesAccountSection() {
        show(SettingsState(appName = "Sample", signedIn = false))
        rule.onNodeWithText("Account").assertDoesNotExist()
        rule.onNodeWithText("Privacy policy").performScrollTo().assertExists()
    }
}
