package com.whitelabel.core.designsystem

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.whitelabel.core.designsystem.component.AppConfirmDialog
import com.whitelabel.core.designsystem.component.AppPrimaryButton
import com.whitelabel.core.designsystem.component.AppTextField
import com.whitelabel.core.designsystem.component.EmptyState
import com.whitelabel.core.designsystem.component.ErrorState
import com.whitelabel.core.designsystem.theme.AppTheme
import org.junit.Rule
import org.junit.Test

class ComponentsTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun string(id: Int) = rule.activity.getString(id)

    private val isPassword = SemanticsMatcher.keyIsDefined(SemanticsProperties.Password)
    private val hasError = SemanticsMatcher.keyIsDefined(SemanticsProperties.Error)

    @Test
    fun passwordField_hidesTextUntilToggled() {
        rule.setContent {
            AppTheme {
                var value by remember { mutableStateOf("secret") }
                AppTextField(value = value, onValueChange = { value = it }, label = "Password", isPassword = true)
            }
        }
        rule.onNode(hasSetTextAction()).assert(isPassword)
        rule.onNodeWithContentDescription(string(R.string.ds_show_password)).performClick()
        rule.onNodeWithContentDescription(string(R.string.ds_hide_password)).assertExists()
        rule.onNode(hasSetTextAction()).assert(!isPassword)
    }

    @Test
    fun textField_error_showsMessageAndErrorSemantics() {
        rule.setContent {
            AppTheme {
                AppTextField(value = "", onValueChange = {}, label = "Email", error = "Invalid email")
            }
        }
        rule.onNodeWithText("Invalid email").assertExists()
        rule.onNode(hasSetTextAction()).assert(hasError)
    }

    @Test
    fun primaryButton_loading_isDisabledAndShowsProgress() {
        rule.setContent {
            AppTheme { AppPrimaryButton(text = "Save", onClick = {}, loading = true) }
        }
        rule.onNodeWithTag("AppButtonProgress").assertExists()
        rule.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun primaryButton_atFontScale2_keepsFullTextAndMinHeight() {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                AppTheme {
                    AppPrimaryButton(text = "Continue", onClick = {}, modifier = Modifier.testTag("button"))
                }
            }
        }
        rule.onNodeWithTag("button").assertTextEquals("Continue").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun errorState_retryInvokesCallback_andIsAbsentWithoutCallback() {
        var retries = 0
        var withCallback by mutableStateOf(true)
        rule.setContent {
            AppTheme {
                ErrorState(message = "Boom", onRetry = if (withCallback) ({ retries++ }) else null)
            }
        }
        rule.onNodeWithText("Boom").assertExists()
        rule.onNodeWithText(string(R.string.ds_retry)).performClick()
        assertThat(retries).isEqualTo(1)
        withCallback = false
        rule.onNodeWithText(string(R.string.ds_retry)).assertDoesNotExist()
    }

    @Test
    fun emptyState_showsTitleMessageAndAction() {
        var actions = 0
        rule.setContent {
            AppTheme {
                EmptyState(title = "Nothing here", message = "Add something", actionLabel = "Add", onAction = { actions++ })
            }
        }
        rule.onNodeWithText("Nothing here").assertExists()
        rule.onNodeWithText("Add something").assertExists()
        rule.onNodeWithText("Add").performClick()
        assertThat(actions).isEqualTo(1)
    }

    @Test
    fun confirmDialog_routesConfirmAndDismiss() {
        var confirmed = 0
        var dismissed = 0
        rule.setContent {
            AppTheme {
                AppConfirmDialog(
                    title = "Delete?", text = "This cannot be undone", confirmLabel = "Delete", dismissLabel = "Cancel",
                    onConfirm = { confirmed++ }, onDismiss = { dismissed++ },
                )
            }
        }
        rule.onNodeWithText("Delete?").assertExists()
        rule.onNodeWithText("Delete").performClick()
        rule.onNodeWithText("Cancel").performClick()
        assertThat(confirmed).isEqualTo(1)
        assertThat(dismissed).isEqualTo(1)
    }
}
