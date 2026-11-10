package com.whitelabel.feature.authentication.presentation.login

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whitelabel.core.designsystem.component.AppPrimaryButton
import com.whitelabel.core.designsystem.component.AppTextButton
import com.whitelabel.core.designsystem.component.AppTextField
import com.whitelabel.core.designsystem.theme.AppTheme
import com.whitelabel.feature.authentication.AuthOptions
import com.whitelabel.feature.authentication.R
import com.whitelabel.feature.authentication.presentation.AuthForm
import com.whitelabel.feature.authentication.presentation.FormError

@Composable
internal fun LoginRoot(
    options: AuthOptions,
    onCreateAccount: () -> Unit,
    onForgotPassword: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LoginScreen(
        state = state,
        appName = context.applicationInfo.loadLabel(context.packageManager).toString(),
        options = options,
        onAction = viewModel::onAction,
        onCreateAccount = onCreateAccount,
        onForgotPassword = onForgotPassword,
    )
}

@Composable
internal fun LoginScreen(
    state: LoginState,
    appName: String,
    options: AuthOptions,
    onAction: (LoginAction) -> Unit,
    onCreateAccount: () -> Unit,
    onForgotPassword: () -> Unit,
) {
    AuthForm(title = stringResource(R.string.auth_sign_in_title, appName), onNavigateBack = null) {
        AppTextField(
            value = state.email,
            onValueChange = { onAction(LoginAction.OnEmailChange(it)) },
            label = stringResource(R.string.auth_email),
            error = state.emailError?.asString(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        AppTextField(
            value = state.password,
            onValueChange = { onAction(LoginAction.OnPasswordChange(it)) },
            label = stringResource(R.string.auth_password),
            isPassword = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        FormError(state.error)
        AppPrimaryButton(
            text = stringResource(R.string.auth_sign_in),
            onClick = { onAction(LoginAction.OnSubmit) },
            enabled = state.canSubmit,
            loading = state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        )
        if (options.allowPasswordReset) {
            AppTextButton(text = stringResource(R.string.auth_forgot_password), onClick = onForgotPassword)
        }
        if (options.allowRegistration) {
            AppTextButton(text = stringResource(R.string.auth_create_account), onClick = onCreateAccount)
        }
    }
}

@Preview
@Composable
private fun LoginScreenPreview() {
    AppTheme {
        LoginScreen(
            state = LoginState(email = "ada@example.com"),
            appName = "Sample",
            options = AuthOptions(),
            onAction = {},
            onCreateAccount = {},
            onForgotPassword = {},
        )
    }
}

@Preview
@Composable
private fun LoginScreenErrorPreview() {
    AppTheme {
        LoginScreen(
            state = LoginState(email = "ada@example.com", password = "x"),
            appName = "Sample",
            options = AuthOptions(allowRegistration = false),
            onAction = {},
            onCreateAccount = {},
            onForgotPassword = {},
        )
    }
}
