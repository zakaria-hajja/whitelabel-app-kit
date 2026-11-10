package com.whitelabel.feature.authentication.presentation.forgot

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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whitelabel.core.designsystem.component.AppPrimaryButton
import com.whitelabel.core.designsystem.component.AppTextButton
import com.whitelabel.core.designsystem.component.AppTextField
import com.whitelabel.core.designsystem.theme.AppTheme
import com.whitelabel.feature.authentication.R
import com.whitelabel.feature.authentication.presentation.AuthForm
import com.whitelabel.feature.authentication.presentation.FormError

@Composable
internal fun ForgotPasswordRoot(onBack: () -> Unit, viewModel: ForgotPasswordViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ForgotPasswordScreen(state = state, onAction = viewModel::onAction, onBack = onBack)
}

@Composable
internal fun ForgotPasswordScreen(state: ForgotPasswordState, onAction: (ForgotPasswordAction) -> Unit, onBack: () -> Unit) {
    AuthForm(title = stringResource(R.string.auth_forgot_title), onNavigateBack = onBack) {
        if (state.sent) {
            Text(stringResource(R.string.auth_forgot_sent), style = MaterialTheme.typography.bodyLarge)
            AppTextButton(text = stringResource(R.string.auth_back_to_sign_in), onClick = onBack)
            return@AuthForm
        }
        Text(stringResource(R.string.auth_forgot_message), style = MaterialTheme.typography.bodyLarge)
        AppTextField(
            value = state.email,
            onValueChange = { onAction(ForgotPasswordAction.OnEmailChange(it)) },
            label = stringResource(R.string.auth_email),
            error = state.emailError?.asString(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        FormError(state.error)
        AppPrimaryButton(
            text = stringResource(R.string.auth_send_link),
            onClick = { onAction(ForgotPasswordAction.OnSubmit) },
            enabled = state.email.isNotBlank() && !state.isSubmitting,
            loading = state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview
@Composable
private fun ForgotPasswordScreenPreview() {
    AppTheme { ForgotPasswordScreen(state = ForgotPasswordState(), onAction = {}, onBack = {}) }
}

@Preview
@Composable
private fun ForgotPasswordSentPreview() {
    AppTheme { ForgotPasswordScreen(state = ForgotPasswordState(sent = true), onAction = {}, onBack = {}) }
}
