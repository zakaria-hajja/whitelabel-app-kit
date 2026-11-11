package com.whitelabel.feature.profile.presentation.profile

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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whitelabel.core.designsystem.component.AppLoadingIndicator
import com.whitelabel.core.designsystem.component.AppPrimaryButton
import com.whitelabel.core.designsystem.component.AppScaffold
import com.whitelabel.core.designsystem.component.AppTextButton
import com.whitelabel.core.designsystem.component.AppTextField
import com.whitelabel.core.designsystem.theme.AppTheme
import com.whitelabel.feature.profile.ProfileOptions
import com.whitelabel.feature.profile.R

@Composable
internal fun ProfileRoot(
    options: ProfileOptions,
    onChangePassword: () -> Unit,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProfileScreen(state = state, options = options, onAction = viewModel::onAction, onChangePassword = onChangePassword)
}

@Composable
internal fun ProfileScreen(
    state: ProfileState,
    options: ProfileOptions,
    onAction: (ProfileAction) -> Unit,
    onChangePassword: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val message = state.message?.asString()
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onAction(ProfileAction.OnMessageShown)
        }
    }
    AppScaffold(title = stringResource(R.string.profile_title), snackbarHostState = snackbarHostState) { innerPadding ->
        if (!state.loaded) {
            AppLoadingIndicator(Modifier.padding(innerPadding).padding(AppTheme.spacing.md))
            return@AppScaffold
        }
        val nameKeyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(AppTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.md),
        ) {
            Column {
                Text(stringResource(R.string.profile_email), style = MaterialTheme.typography.labelLarge)
                Text(state.email, style = MaterialTheme.typography.bodyLarge)
            }
            AppTextField(
                value = state.firstName,
                onValueChange = { onAction(ProfileAction.OnFirstNameChange(it)) },
                label = stringResource(R.string.profile_first_name),
                error = state.firstNameError?.asString(),
                keyboardOptions = nameKeyboard,
                modifier = Modifier.fillMaxWidth(),
            )
            AppTextField(
                value = state.lastName,
                onValueChange = { onAction(ProfileAction.OnLastNameChange(it)) },
                label = stringResource(R.string.profile_last_name),
                error = state.lastNameError?.asString(),
                keyboardOptions = nameKeyboard.copy(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            AppPrimaryButton(
                text = stringResource(R.string.profile_save),
                onClick = { onAction(ProfileAction.OnSave) },
                enabled = state.canSave,
                loading = state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
            if (options.allowPasswordChange) {
                AppTextButton(text = stringResource(R.string.profile_change_password), onClick = onChangePassword)
            }
        }
    }
}

@Preview
@Composable
private fun ProfileScreenPreview() {
    AppTheme {
        ProfileScreen(
            state = ProfileState(loaded = true, email = "ada@example.com", firstName = "Ada", savedFirstName = "Ada", lastName = "Lovelace"),
            options = ProfileOptions(),
            onAction = {},
            onChangePassword = {},
        )
    }
}

@Preview
@Composable
private fun ProfileScreenErrorPreview() {
    AppTheme(darkTheme = true) {
        ProfileScreen(
            state = ProfileState(loaded = true, email = "ada@example.com", firstName = "x".repeat(51), firstNameError = null),
            options = ProfileOptions(allowPasswordChange = false),
            onAction = {},
            onChangePassword = {},
        )
    }
}
