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
import androidx.compose.ui.test.onAllNodesWithTag
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThanOrEqualTo
import com.whitelabel.core.designsystem.theme.StylePack
import com.whitelabel.core.designsystem.theme.ValueRowLayout
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.height
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val actions = mutableListOf<SettingsAction>()

    private fun show(state: SettingsState, options: SettingsOptions = SettingsOptions(), pack: StylePack = StylePack.Material) {
        rule.setContent { Screen(state, options, pack) }
    }

    @androidx.compose.runtime.Composable
    private fun Screen(state: SettingsState, options: SettingsOptions, pack: StylePack = StylePack.Material) {
        AppTheme(stylePack = pack) {
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
    fun aboutIsOneLine() {
        show(SettingsState(appName = "Sample", versionName = "1.0.0"), SettingsOptions(aboutNote = R.string.settings_retry))
        rule.onNodeWithText("Sample 1.0.0 · Retry").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aboutWithoutANoteIsNameAndVersion() {
        show(SettingsState(appName = "Sample", versionName = "1.0.0"))
        rule.onNodeWithText("Sample 1.0.0").performScrollTo().assertIsDisplayed()
    }

    /** An app's About rows (e.g. open-source licences) come under Terms, in the app's order, above Diagnostics. */
    @Test
    fun aboutRows_renderUnderTermsInOrder() {
        val options = SettingsOptions(
            aboutRows = {
                SettingsLinkRow("Open-source licences", onClick = {})
                SettingsLinkRow("Credits", onClick = {})
            },
        )
        show(SettingsState(appName = "Sample", showDiagnostics = true), options)
        rule.onNodeWithText("Diagnostics").performScrollTo()
        assertThat(top("Terms of use")).isLessThan(top("Open-source licences"))
        assertThat(top("Open-source licences")).isLessThan(top("Credits"))
        assertThat(top("Credits")).isLessThan(top("Diagnostics"))
        rule.onNodeWithText("Open-source licences").assertExists()
    }

    /** By default there are no About rows: Diagnostics follows Terms one row down, as Terms follows Privacy. */
    @Test
    fun noAboutRows_keepsAboutAsItWas() {
        show(SettingsState(appName = "Sample", showDiagnostics = true), SettingsOptions())
        rule.onNodeWithText("Diagnostics").performScrollTo()
        val rowStep = top("Terms of use") - top("Privacy policy")
        assertThat(kotlin.math.abs((top("Diagnostics") - top("Terms of use") - rowStep).value)).isLessThan(0.5f)
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

    private fun bounds(text: String) = rule.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot()

    @Test
    fun valueRowStacksUnderGallery() {
        show(SettingsState(appName = "Sample"), pack = StylePack.Gallery)
        assertThat(bounds("System default").top).isGreaterThanOrEqualTo(bounds("Theme").bottom)
        rule.onNodeWithText("APPEARANCE").assertExists()
    }

    @Test
    fun materialKeepsInlineRowsAndAccentHeaders() {
        show(SettingsState(appName = "Sample"))
        assertThat(bounds("System default").top).isLessThan(bounds("Theme").bottom)
        rule.onNodeWithText("Appearance").assertExists()
        rule.onNodeWithText("APPEARANCE").assertDoesNotExist()
    }

    @Test
    fun stackedRowIsOneButton() {
        show(SettingsState(appName = "Sample"), pack = StylePack.Gallery)
        rule.onNodeWithText("Theme")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(hasText("System default"))
    }

    @Test
    fun aLongStackedValueWraps() {
        rule.setContent {
            AppTheme(stylePack = StylePack.Gallery) {
                SettingsValueRow("Default reminder", "A very long value that cannot fit on one line at all, not even close", onClick = {})
            }
        }
        val label = bounds("Default reminder")
        val value = bounds("A very long value that cannot fit on one line at all, not even close")
        assertThat(value.left).isEqualTo(label.left)
        assertThat(value.top).isGreaterThanOrEqualTo(label.bottom)
    }

    @Test
    fun onlyEmptySectionsVanish() {
        val empty = SettingsSection(null) {}
        val callout = SettingsSection(null) { Text("Blocked") }
        var withCallout by mutableStateOf(false)
        rule.setContent {
            Screen(SettingsState(appName = "Sample"), SettingsOptions(sections = if (withCallout) listOf(empty, callout) else listOf(empty)))
        }
        val withoutCallout = rule.onAllNodesWithTag("SettingsDivider").fetchSemanticsNodes().size
        withCallout = true
        rule.waitForIdle()
        assertThat(rule.onAllNodesWithTag("SettingsDivider").fetchSemanticsNodes().size).isEqualTo(withoutCallout + 1)
        assertThat(withoutCallout).isEqualTo(2) // Appearance and About: the empty section drew none.
    }

    @Test
    fun linksAreRowsAtTheGutter() {
        show(SettingsState(appName = "Sample"), pack = StylePack.Gallery)
        rule.onNodeWithText("Privacy policy").performScrollTo()
        rule.onNode(hasText("Privacy policy") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).assertExists()
        // 20 dp gutter, within pixel rounding.
        assertThat(kotlin.math.abs((bounds("Privacy policy").left - 20.dp).value)).isLessThan(0.5f)
    }

    /** Material rows keep their 48 dp height, the same as the toggle rows beside them. */
    @Test fun materialValueRowKeepsItsHeight() {
        rule.setContent { AppTheme(stylePack = StylePack.Material) { SettingsValueRow("Theme", "System default", onClick = {}) } }
        assertThat(kotlin.math.abs((rule.onNodeWithText("Theme").getUnclippedBoundsInRoot().height - 48.dp).value)).isLessThan(0.5f)
    }

    private val disclosure = StylePack.Gallery.copy(valueRows = ValueRowLayout.Disclosure)

    private fun disclosureRow(value: String, onClick: (() -> Unit)?) {
        rule.setContent {
            AppTheme(stylePack = disclosure) {
                SettingsValueRow("Remind me at", value, onClick = onClick, modifier = Modifier.testTag("row"))
            }
        }
    }

    /** P-17: the value hugs the trailing edge, then a chevron at the gutter (as iOS). */
    @Test fun disclosureRow_valueHugsTheChevronAtTheEnd() {
        disclosureRow("20:00", onClick = {})
        val row = rule.onNodeWithTag("row").getUnclippedBoundsInRoot()
        val chevron = rule.onNodeWithTag(SETTINGS_ROW_CHEVRON, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val value = bounds("20:00")
        assertThat(kotlin.math.abs((row.right - 20.dp - chevron.right).value)).isLessThan(0.5f)
        assertThat(chevron.left - value.right).isLessThan(12.dp)
        assertThat(value.left).isGreaterThan(row.right - (row.right - row.left) / 2)
    }

    @Test fun disclosureRow_readOnlyHasNoChevron() {
        disclosureRow("20:00", onClick = null)
        rule.onNodeWithTag(SETTINGS_ROW_CHEVRON, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun disclosureRow_isOneButton() {
        disclosureRow("20:00", onClick = {})
        rule.onNodeWithText("Remind me at")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(hasText("20:00"))
    }

    /** A long value wraps in at most half the row instead of crushing the label. */
    @Test fun disclosureRow_longValueKeepsToHalfTheRow() {
        disclosureRow("Two days before, at 20:00, and again on the morning", onClick = {})
        val row = rule.onNodeWithTag("row").getUnclippedBoundsInRoot()
        val value = bounds("Two days before, at 20:00, and again on the morning")
        assertThat(value.right - value.left).isLessThan((row.right - row.left) / 2 + 0.5.dp)
        assertThat(bounds("Remind me at").right).isLessThan(value.left + 0.5.dp)
    }

    /** P-19: an app with its own privacy row drops About's, keeping Terms. */
    @Test fun aboutPrivacyLink_canBeDropped() {
        show(SettingsState(appName = "Sample"), SettingsOptions(showPrivacyLink = false))
        rule.onNodeWithText("Terms of use").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Privacy policy").assertDoesNotExist()
    }

    @Test fun aboutPrivacyLink_showsByDefault() {
        show(SettingsState(appName = "Sample"))
        rule.onNodeWithText("Privacy policy").performScrollTo().assertIsDisplayed()
    }
}
