package com.whitelabel.feature.profile

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import assertk.assertThat
import assertk.assertions.contains
import com.whitelabel.core.designsystem.theme.AppTheme
import com.whitelabel.feature.profile.presentation.profile.ProfileAction
import com.whitelabel.feature.profile.presentation.profile.ProfileScreen
import com.whitelabel.feature.profile.presentation.profile.ProfileState
import org.junit.Rule
import org.junit.Test

class ProfileScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var state by mutableStateOf(
        ProfileState(loaded = true, email = "ada@example.com", firstName = "Ada", savedFirstName = "Ada"),
    )
    private val actions = mutableListOf<ProfileAction>()

    private fun show(options: ProfileOptions = ProfileOptions()) {
        rule.setContent {
            AppTheme {
                ProfileScreen(
                    state = state,
                    options = options,
                    onAction = { action ->
                        actions += action
                        if (action is ProfileAction.OnLastNameChange) state = state.copy(lastName = action.name)
                    },
                    onChangePassword = {},
                )
            }
        }
    }

    @Test
    fun editName_thenSave_sendsSave() {
        show()
        rule.onNodeWithText("ada@example.com").assertExists()
        rule.onNodeWithText("Save").assertIsNotEnabled()
        rule.onNodeWithText("Last name").performTextInput("Lovelace")
        rule.onNodeWithText("Save").performClick()
        rule.runOnIdle { assertThat(actions).contains(ProfileAction.OnSave) }
    }

    @Test
    fun passwordChangeDisabled_hidesTheRow() {
        show(ProfileOptions(allowPasswordChange = false))
        rule.onNodeWithText("Change password").assertDoesNotExist()
    }
}
