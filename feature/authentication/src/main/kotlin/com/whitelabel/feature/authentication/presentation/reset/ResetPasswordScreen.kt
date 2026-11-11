package com.whitelabel.feature.authentication.presentation.reset

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whitelabel.core.designsystem.component.AppPrimaryButton
import com.whitelabel.core.designsystem.component.AppTextField
import com.whitelabel.core.designsystem.theme.AppTheme
import com.whitelabel.feature.authentication.R
import com.whitelabel.feature.authentication.presentation.AuthForm
import com.whitelabel.feature.authentication.presentation.FormError

@Composable
internal fun ResetPasswordRoot(
    onBackToSignIn: () -> Unit,
    onRequestNewLink: () -> Unit,
    viewModel: ResetPasswordViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ResetPasswordScreen(
        state = state,
        onAction = viewModel::onAction,
        onBackToSignIn = onBackToSignIn,
        onRequestNewLink = onRequestNewLink,
    )
}

@Composable
internal fun ResetPasswordScreen(
    state: ResetPasswordState,
    onAction: (ResetPasswordAction) -> Unit,
    onBackToSignIn: () -> Unit,
    onRequestNewLink: () -> Unit,
) {
    AuthForm(title = stringResource(R.string.auth_reset_title), onNavigateBack = onBackToSignIn) {
        when {
            state.done -> {
                Text(stringResource(R.string.auth_reset_done), style = MaterialTheme.typography.bodyLarge)
                AppPrimaryButton(
                    text = stringResource(R.string.auth_back_to_sign_in),
                    onClick = onBackToSignIn,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            state.tokenExpired -> {
                Text(stringResource(R.string.auth_error_reset_expired), style = MaterialTheme.typography.bodyLarge)
                AppPrimaryButton(
                    text = stringResource(R.string.auth_request_new_link),
                    onClick = onRequestNewLink,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            else -> {
                AppTextField(
                    value = state.password,
                    onValueChange = { onAction(ResetPasswordAction.OnPasswordChange(it)) },
                    label = stringResource(R.string.auth_new_password),
                    error = state.passwordError?.asString(),
                    isPassword = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                FormError(state.error)
                AppPrimaryButton(
                    text = stringResource(R.string.auth_reset_submit),
                    onClick = { onAction(ResetPasswordAction.OnSubmit) },
                    enabled = state.password.isNotEmpty() && !state.isSubmitting,
                    loading = state.isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Preview
@Composable
private fun ResetPasswordScreenPreview() {
    AppTheme { ResetPasswordScreen(ResetPasswordState(), onAction = {}, onBackToSignIn = {}, onRequestNewLink = {}) }
}

@Preview
@Composable
private fun ResetPasswordExpiredPreview() {
    AppTheme { ResetPasswordScreen(ResetPasswordState(tokenExpired = true), onAction = {}, onBackToSignIn = {}, onRequestNewLink = {}) }
}
