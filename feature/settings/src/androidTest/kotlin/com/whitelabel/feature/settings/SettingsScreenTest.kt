package com.whitelabel.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import assertk.assertThat
import androidx.compose.material3.Text
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.dp
import assertk.assertions.containsExactly
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
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

    private fun show(state: SettingsState, options: SettingsOptions = SettingsOptions()) {
        rule.setContent { Screen(state, options) }
    }

    @androidx.compose.runtime.Composable
    private fun Screen(state: SettingsState, options: SettingsOptions) {
        AppTheme {
            SettingsScreen(state, options, onAction = { actions += it }, onOpenUrl = {}, onOpenDiagnostics = {})
        }
    }

    @Test
    fun selectDarkTheme_sendsThemeAction() {
        show(SettingsState(appName = "Sample"))
        rule.onNodeWithText("Theme").performClick()
        rule.onNodeWithText("Dark").performClick()
        rule.runOnIdle { assertThat(actions).containsExactly(SettingsAction.OnThemeSelect(ThemePreference.DARK)) }
        rule.onNodeWithText("Done").assertExists()
    }

    @Test
    fun themeRow_showsCurrentValue() {
        show(SettingsState(appName = "Sample", theme = ThemePreference.DARK))
        rule.onNodeWithText("Dark").assertExists()
        rule.onNodeWithText("Light").assertDoesNotExist()
    }

    @Test
    fun done_closesTheSheet() {
        show(SettingsState(appName = "Sample"))
        rule.onNodeWithText("Theme").performClick()
        rule.onNodeWithText("Done").performClick()
        rule.onNodeWithText("Light").assertDoesNotExist()
    }

    @Test
    fun languageRow_opensLanguageChoices() {
        show(SettingsState(appName = "Sample", remoteLanguages = emptyList()), SettingsOptions(languages = listOf("en", "fr")))
        rule.onNodeWithText("Français").assertDoesNotExist()
        rule.onNodeWithText("Language").performClick()
        rule.onNodeWithText("Français").performClick()
        rule.runOnIdle { assertThat(actions).containsExactly(SettingsAction.OnLanguageSelect("fr")) }
    }

    @Test
    fun valueRow_isOneButton() {
        show(SettingsState(appName = "Sample"))
        rule.onNodeWithText("Theme")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(hasText("System default"))
    }

    @Test
    fun openSheet_survivesRecreation() {
        val tester = StateRestorationTester(rule)
        tester.setContent { Screen(SettingsState(appName = "Sample"), SettingsOptions()) }
        rule.onNodeWithText("Light").assertDoesNotExist()
        rule.onNodeWithText("Theme").performClick()
        tester.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("Light").assertExists()
    }

    private fun top(text: String) = rule.onNodeWithText(text).getUnclippedBoundsInRoot().top

    @Test
    fun appSection_rendersAboveTheme() {
        val currency = SettingsSection(R.string.settings_account) { SettingsValueRow("Currency", "Euro (€)", onClick = {}) }
        show(SettingsState(appName = "Sample"), SettingsOptions(sections = listOf(currency)))
        rule.onNodeWithText("Account").assertExists()
        assertThat(top("Currency")).isLessThan(top("Theme"))
    }

    @Test
    fun untitledSection_drawsNoHeader() {
        val callout = SettingsSection(null) { Text("Reminders are blocked") }
        show(SettingsState(appName = "Sample"), SettingsOptions(sections = listOf(callout)))
        rule.onNodeWithText("Reminders are blocked").assertExists()
        assertThat(top("Reminders are blocked")).isLessThan(top("Appearance"))
    }

    @Test
    fun longValue_wrapsInsteadOfCrushingTheLabel() {
        val long = "Dollar des États-Unis ($) et encore quelques mots pour remplir la ligne"
        val row = SettingsSection(null) { SettingsValueRow("Currency", long, onClick = {}) }
        show(SettingsState(appName = "Sample"), SettingsOptions(sections = listOf(row)))
        val label = rule.onNodeWithText("Currency", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertThat(label.right - label.left).isGreaterThan(64.dp)
    }

    @Test
    fun aboutNote_showsUnderVersion() {
        show(SettingsState(appName = "Sample", versionName = "1.0.0"), SettingsOptions(aboutNote = R.string.settings_retry))
        rule.onNodeWithText("Retry").performScrollTo()
        assertThat(top("Retry")).isGreaterThan(top("Version 1.0.0"))
    }

    @Test
    fun noSections_keepsTheSharedScreen() {
        show(SettingsState(appName = "Sample"))
        assertThat(top("Appearance")).isLessThan(top("Theme"))
        rule.onNodeWithText("Retry").assertDoesNotExist()
    }

    /** Munch offers 15 reminder days and 14 currencies: on a small phone the list must scroll, Done must stay reachable. */
    @Test
    fun longChoiceList_scrollsToTheLastChoice() {
        val picked = mutableListOf<Int>()
        rule.setContent {
            AppTheme {
                SettingsChoiceSheet(
                    title = "Pick",
                    choices = (1..40).toList(),
                    selected = 1,
                    label = { "Choice $it" },
                    onSelect = { picked += it },
                    onDismiss = {},
                )
            }
        }
        rule.onNodeWithText("Choice 40").performScrollTo().performClick()
        rule.runOnIdle { assertThat(picked).containsExactly(40) }
        rule.onNodeWithText("Done").assertIsDisplayed()
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
