package com.whitelabel.feature.authentication

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
import assertk.assertions.containsExactly
import com.whitelabel.core.designsystem.theme.AppTheme
import com.whitelabel.feature.authentication.presentation.login.LoginAction
import com.whitelabel.feature.authentication.presentation.login.LoginScreen
import com.whitelabel.feature.authentication.presentation.login.LoginState
import org.junit.Rule
import org.junit.Test

class LoginScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var state by mutableStateOf(LoginState())
    private val submitted = mutableListOf<Pair<String, String>>()

    private fun show(options: AuthOptions = AuthOptions()) {
        rule.setContent {
            AppTheme {
                LoginScreen(
                    state = state,
                    appName = "Sample",
                    options = options,
                    onAction = { action ->
                        when (action) {
                            is LoginAction.OnEmailChange -> state = state.copy(email = action.email)
                            is LoginAction.OnPasswordChange -> state = state.copy(password = action.password)
                            LoginAction.OnSubmit -> submitted += state.email to state.password
                        }
                    },
                    onCreateAccount = {},
                    onForgotPassword = {},
                )
            }
        }
    }

    @Test
    fun typeEmailAndPassword_thenSignIn_submitsBoth() {
        show()
        rule.onNodeWithText("Sign in").assertIsNotEnabled()
        rule.onNodeWithText("Email").performTextInput("ada@example.com")
        rule.onNodeWithText("Password").performTextInput("secret pass")
        rule.onNodeWithText("Sign in").performClick()
        rule.runOnIdle { assertThat(submitted).containsExactly("ada@example.com" to "secret pass") }
    }

    @Test
    fun registrationDisabled_hidesCreateAccount() {
        show(AuthOptions(allowRegistration = false))
        rule.onNodeWithText("Create an account").assertDoesNotExist()
        rule.onNodeWithText("Forgot password?").assertExists()
    }
}
