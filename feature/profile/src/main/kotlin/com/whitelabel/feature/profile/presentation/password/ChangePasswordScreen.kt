package com.whitelabel.feature.profile.presentation.password

import com.whitelabel.core.presentation.asString
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import com.whitelabel.core.designsystem.component.AppScaffold
import com.whitelabel.core.designsystem.component.AppTextField
import com.whitelabel.core.designsystem.theme.AppTheme
import com.whitelabel.feature.profile.R

@Composable
internal fun ChangePasswordRoot(onBack: () -> Unit, viewModel: ChangePasswordViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ChangePasswordScreen(state = state, onAction = viewModel::onAction, onBack = onBack)
}

@Composable
internal fun ChangePasswordScreen(state: ChangePasswordState, onAction: (ChangePasswordAction) -> Unit, onBack: () -> Unit) {
    AppScaffold(title = stringResource(R.string.profile_change_password_title), onNavigateBack = onBack) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(AppTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.md),
        ) {
            if (state.done) {
                Text(stringResource(R.string.profile_password_changed), style = MaterialTheme.typography.bodyLarge)
                AppPrimaryButton(text = stringResource(R.string.profile_back), onClick = onBack, modifier = Modifier.fillMaxWidth())
                return@Column
            }
            val password = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next)
            AppTextField(
                value = state.current,
                onValueChange = { onAction(ChangePasswordAction.OnCurrentChange(it)) },
                label = stringResource(R.string.profile_current_password),
                error = state.currentError?.asString(),
                isPassword = true,
                keyboardOptions = password,
                modifier = Modifier.fillMaxWidth(),
            )
            AppTextField(
                value = state.new,
                onValueChange = { onAction(ChangePasswordAction.OnNewChange(it)) },
                label = stringResource(R.string.profile_new_password),
                error = state.newError?.asString(),
                isPassword = true,
                keyboardOptions = password,
                modifier = Modifier.fillMaxWidth(),
            )
            AppTextField(
                value = state.confirm,
                onValueChange = { onAction(ChangePasswordAction.OnConfirmChange(it)) },
                label = stringResource(R.string.profile_confirm_password),
                error = state.confirmError?.asString(),
                isPassword = true,
                keyboardOptions = password.copy(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            state.error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            AppPrimaryButton(
                text = stringResource(R.string.profile_change_password_submit),
                onClick = { onAction(ChangePasswordAction.OnSubmit) },
                enabled = state.canSubmit,
                loading = state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview
@Composable
private fun ChangePasswordScreenPreview() {
    AppTheme { ChangePasswordScreen(ChangePasswordState(current = "x"), onAction = {}, onBack = {}) }
}

@Preview
@Composable
private fun ChangePasswordDonePreview() {
    AppTheme(darkTheme = true) { ChangePasswordScreen(ChangePasswordState(done = true), onAction = {}, onBack = {}) }
}
