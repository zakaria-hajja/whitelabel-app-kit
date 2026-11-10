package com.whitelabel.feature.authentication.presentation.register

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whitelabel.core.designsystem.component.AppPrimaryButton
import com.whitelabel.core.designsystem.component.AppTextField
import com.whitelabel.core.designsystem.theme.AppTheme
import com.whitelabel.feature.authentication.R
import com.whitelabel.feature.authentication.presentation.AuthForm
import com.whitelabel.feature.authentication.presentation.FormError

@Composable
internal fun RegisterRoot(onBack: () -> Unit, viewModel: RegisterViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RegisterScreen(state = state, onAction = viewModel::onAction, onBack = onBack)
}

@Composable
internal fun RegisterScreen(state: RegisterState, onAction: (RegisterAction) -> Unit, onBack: () -> Unit) {
    val nameKeyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
    AuthForm(title = stringResource(R.string.auth_register_title), onNavigateBack = onBack) {
        AppTextField(
            value = state.email,
            onValueChange = { onAction(RegisterAction.OnEmailChange(it)) },
            label = stringResource(R.string.auth_email),
            error = state.emailError?.asString(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        AppTextField(
            value = state.password,
            onValueChange = { onAction(RegisterAction.OnPasswordChange(it)) },
            label = stringResource(R.string.auth_password),
            error = state.passwordError?.asString(),
            isPassword = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        AppTextField(
            value = state.firstName,
            onValueChange = { onAction(RegisterAction.OnFirstNameChange(it)) },
            label = stringResource(R.string.auth_first_name),
            error = state.firstNameError?.asString(),
            keyboardOptions = nameKeyboard,
            modifier = Modifier.fillMaxWidth(),
        )
        AppTextField(
            value = state.lastName,
            onValueChange = { onAction(RegisterAction.OnLastNameChange(it)) },
            label = stringResource(R.string.auth_last_name),
            error = state.lastNameError?.asString(),
            keyboardOptions = nameKeyboard.copy(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        FormError(state.error)
        AppPrimaryButton(
            text = stringResource(R.string.auth_register),
            onClick = { onAction(RegisterAction.OnSubmit) },
            enabled = state.canSubmit,
            loading = state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview
@Composable
private fun RegisterScreenPreview() {
    AppTheme { RegisterScreen(state = RegisterState(email = "ada@example.com"), onAction = {}, onBack = {}) }
}
