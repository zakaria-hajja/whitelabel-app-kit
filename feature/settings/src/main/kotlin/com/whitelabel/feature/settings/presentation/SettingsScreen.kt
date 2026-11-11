package com.whitelabel.feature.settings.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whitelabel.core.designsystem.component.AppConfirmDialog
import com.whitelabel.core.designsystem.component.AppLoadingIndicator
import com.whitelabel.core.designsystem.component.AppScaffold
import com.whitelabel.core.designsystem.component.AppSecondaryButton
import com.whitelabel.core.designsystem.component.AppTextButton
import com.whitelabel.core.designsystem.component.ErrorState
import com.whitelabel.core.designsystem.theme.AppTheme
import com.whitelabel.core.domain.ThemePreference
import com.whitelabel.feature.settings.R
import com.whitelabel.feature.settings.SettingsOptions
import com.whitelabel.feature.settings.domain.NotificationKind
import com.whitelabel.feature.settings.domain.NotificationSettings
import java.util.Locale

@Composable
internal fun SettingsRoot(
    options: SettingsOptions,
    onOpenUrl: (String) -> Unit,
    onOpenDiagnostics: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The language can change in the system's app-language settings while the screen is away.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onAction(SettingsAction.OnScreenResumed) }
    SettingsScreen(
        state = state,
        options = options,
        onAction = viewModel::onAction,
        onOpenUrl = onOpenUrl,
        onOpenDiagnostics = onOpenDiagnostics,
    )
}

@Composable
internal fun SettingsScreen(
    state: SettingsState,
    options: SettingsOptions,
    onAction: (SettingsAction) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val message = state.message?.asString()
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onAction(SettingsAction.OnMessageShown)
        }
    }
    AppScaffold(title = stringResource(R.string.settings_title), snackbarHostState = snackbarHostState) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = AppTheme.spacing.sm),
        ) {
            Section(stringResource(R.string.settings_theme)) {
                Column(Modifier.selectableGroup()) {
                    ThemePreference.entries.forEach { theme ->
                        ChoiceRow(label = theme.label(), selected = state.theme == theme) {
                            onAction(SettingsAction.OnThemeSelect(theme))
                        }
                    }
                }
            }
            val languages = offeredLanguages(options.languages, state.remoteLanguages)
            if (languages.isNotEmpty()) {
                Section(stringResource(R.string.settings_language)) {
                    Column(Modifier.selectableGroup()) {
                        ChoiceRow(stringResource(R.string.settings_language_system), selected = state.language == null) {
                            onAction(SettingsAction.OnLanguageSelect(null))
                        }
                        languages.forEach { tag ->
                            ChoiceRow(languageName(tag), selected = state.language == tag) {
                                onAction(SettingsAction.OnLanguageSelect(tag))
                            }
                        }
                    }
                }
            }
            if (options.showNotifications && state.signedIn) {
                Section(stringResource(R.string.settings_notifications)) {
                    NotificationsSection(state, onAction)
                }
            }
            Section(stringResource(R.string.settings_about)) {
                Text(
                    text = stringResource(R.string.settings_version, state.versionName),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = AppTheme.spacing.md, vertical = AppTheme.spacing.sm),
                )
                state.supportEmail?.let { email ->
                    LinkRow(stringResource(R.string.settings_contact_support)) { onOpenUrl("mailto:$email") }
                }
                LinkRow(stringResource(R.string.settings_privacy)) { onOpenUrl(state.privacyUrl) }
                LinkRow(stringResource(R.string.settings_terms)) { onOpenUrl(state.termsUrl) }
                if (state.showDiagnostics) LinkRow(stringResource(R.string.settings_diagnostics), onOpenDiagnostics)
            }
            if (state.signedIn) {
                Section(stringResource(R.string.settings_account)) {
                    AppSecondaryButton(
                        text = stringResource(R.string.settings_sign_out),
                        onClick = { onAction(SettingsAction.OnSignOutClick) },
                        modifier = Modifier.padding(horizontal = AppTheme.spacing.md),
                    )
                }
            }
        }
    }
    if (state.confirmSignOut) {
        AppConfirmDialog(
            title = stringResource(R.string.settings_sign_out_title),
            text = stringResource(R.string.settings_sign_out_text, state.appName),
            confirmLabel = stringResource(R.string.settings_sign_out),
            dismissLabel = stringResource(R.string.settings_cancel),
            onConfirm = { onAction(SettingsAction.OnSignOutConfirm) },
            onDismiss = { onAction(SettingsAction.OnSignOutDismiss) },
        )
    }
}

@Composable
private fun NotificationsSection(state: SettingsState, onAction: (SettingsAction) -> Unit) {
    val notifications = state.notifications
    when {
        notifications != null -> {
            ToggleRow(stringResource(R.string.settings_notifications_push), notifications.push) {
                onAction(SettingsAction.OnNotificationToggle(NotificationKind.PUSH, it))
            }
            ToggleRow(stringResource(R.string.settings_notifications_email), notifications.email) {
                onAction(SettingsAction.OnNotificationToggle(NotificationKind.EMAIL, it))
            }
            ToggleRow(stringResource(R.string.settings_notifications_marketing), notifications.marketing) {
                onAction(SettingsAction.OnNotificationToggle(NotificationKind.MARKETING, it))
            }
        }
        state.notificationsError != null -> ErrorState(
            message = state.notificationsError.asString(),
            onRetry = { onAction(SettingsAction.OnRetryNotifications) },
        )
        else -> AppLoadingIndicator(Modifier.padding(AppTheme.spacing.md))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = AppTheme.spacing.md, vertical = AppTheme.spacing.sm),
        )
        content()
        HorizontalDivider(Modifier.padding(top = AppTheme.spacing.sm))
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(horizontal = AppTheme.spacing.md),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = AppTheme.spacing.md))
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, onValueChange = onToggle, role = Role.Switch)
            .padding(horizontal = AppTheme.spacing.md),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun LinkRow(label: String, onClick: () -> Unit) {
    AppTextButton(text = label, onClick = onClick, modifier = Modifier.padding(horizontal = AppTheme.spacing.sm))
}

@Composable
private fun ThemePreference.label(): String = stringResource(
    when (this) {
        ThemePreference.SYSTEM -> R.string.settings_theme_system
        ThemePreference.LIGHT -> R.string.settings_theme_light
        ThemePreference.DARK -> R.string.settings_theme_dark
    },
)

/** A language's own name ("Français"), so it reads correctly whatever the current language is. */
@Composable
private fun languageName(tag: String): String {
    val locale = Locale.forLanguageTag(tag)
    return locale.getDisplayName(locale).replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
}

private val previewState = SettingsState(
    theme = ThemePreference.DARK,
    signedIn = true,
    notifications = NotificationSettings(push = true, email = false, marketing = false),
    appName = "Sample",
    versionName = "1.0.0",
    supportEmail = "help@sample.example",
    showDiagnostics = true,
)

@Preview
@Composable
private fun SettingsScreenPreview() {
    AppTheme { SettingsScreen(previewState, SettingsOptions(), onAction = {}, onOpenUrl = {}, onOpenDiagnostics = {}) }
}

@Preview
@Composable
private fun SettingsScreenSignedOutPreview() {
    AppTheme(darkTheme = true) {
        SettingsScreen(
            previewState.copy(signedIn = false, notifications = null, showDiagnostics = false),
            SettingsOptions(),
            onAction = {},
            onOpenUrl = {},
            onOpenDiagnostics = {},
        )
    }
}

