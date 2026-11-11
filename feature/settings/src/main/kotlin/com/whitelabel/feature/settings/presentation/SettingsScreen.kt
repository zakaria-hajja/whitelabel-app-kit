package com.whitelabel.feature.settings.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whitelabel.core.designsystem.component.AppConfirmDialog
import com.whitelabel.core.designsystem.component.AppLoadingIndicator
import com.whitelabel.core.designsystem.component.AppScaffold
import com.whitelabel.core.designsystem.component.AppSecondaryButton
import com.whitelabel.core.designsystem.component.AppSectionHeader
import com.whitelabel.core.designsystem.component.ErrorState
import com.whitelabel.core.designsystem.theme.AppTheme
import com.whitelabel.core.domain.ThemePreference
import com.whitelabel.core.presentation.asString
import com.whitelabel.feature.settings.R
import com.whitelabel.feature.settings.SettingsChoiceSheet
import com.whitelabel.feature.settings.SettingsLinkRow
import com.whitelabel.feature.settings.SettingsOptions
import com.whitelabel.feature.settings.SettingsValueRow
import com.whitelabel.feature.settings.domain.NotificationKind
import com.whitelabel.feature.settings.domain.NotificationSettings
import java.util.Locale
import org.koin.androidx.compose.koinViewModel

@Composable
internal fun SettingsRoot(
    options: SettingsOptions,
    onOpenUrl: (String) -> Unit,
    onOpenDiagnostics: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
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
    // Which choice sheet is open; saved so it survives rotation.
    var openSheet by rememberSaveable { mutableStateOf<String?>(null) }
    val languages = offeredLanguages(options.languages, state.remoteLanguages)
    AppScaffold(title = stringResource(R.string.settings_title), snackbarHostState = snackbarHostState) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = AppTheme.spacing.sm),
        ) {
            options.sections.forEach { section ->
                Section(section.title?.let { stringResource(it) }, section.content)
            }
            Section(stringResource(R.string.settings_appearance)) {
                SettingsValueRow(
                    label = stringResource(R.string.settings_theme),
                    value = state.theme.label(),
                    onClick = { openSheet = SHEET_THEME },
                )
                if (languages.isNotEmpty()) {
                    SettingsValueRow(
                        label = stringResource(R.string.settings_language),
                        value = state.language?.let { languageName(it) } ?: stringResource(R.string.settings_language_system),
                        onClick = { openSheet = SHEET_LANGUAGE },
                    )
                }
            }
            if (options.showNotifications && state.signedIn) {
                Section(stringResource(R.string.settings_notifications)) {
                    NotificationsSection(state, onAction)
                }
            }
            Section(stringResource(R.string.settings_about)) {
                // One muted line: "Munch 0.1.0 · Your data stays on this phone."
                val nameAndVersion = stringResource(R.string.settings_about_line, state.appName, state.versionName)
                Text(
                    text = options.aboutNote?.let { "$nameAndVersion · ${stringResource(it)}" } ?: nameAndVersion,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = AppTheme.style.sizing.screenGutter, vertical = AppTheme.spacing.sm),
                )
                state.supportEmail?.let { email ->
                    SettingsLinkRow(stringResource(R.string.settings_contact_support), onClick = { onOpenUrl("mailto:$email") })
                }
                SettingsLinkRow(stringResource(R.string.settings_privacy), onClick = { onOpenUrl(state.privacyUrl) })
                SettingsLinkRow(stringResource(R.string.settings_terms), onClick = { onOpenUrl(state.termsUrl) })
                if (state.showDiagnostics) SettingsLinkRow(stringResource(R.string.settings_diagnostics), onClick = onOpenDiagnostics)
            }
            if (state.signedIn) {
                Section(stringResource(R.string.settings_account)) {
                    AppSecondaryButton(
                        text = stringResource(R.string.settings_sign_out),
                        onClick = { onAction(SettingsAction.OnSignOutClick) },
                        modifier = Modifier.padding(horizontal = AppTheme.style.sizing.screenGutter),
                    )
                }
            }
        }
    }
    when (openSheet) {
        SHEET_THEME -> SettingsChoiceSheet(
            title = stringResource(R.string.settings_theme),
            choices = ThemePreference.entries,
            selected = state.theme,
            label = { it.label() },
            onSelect = { onAction(SettingsAction.OnThemeSelect(it)) },
            onDismiss = { openSheet = null },
        )
        SHEET_LANGUAGE -> SettingsChoiceSheet(
            title = stringResource(R.string.settings_language),
            choices = listOf<String?>(null) + languages,
            selected = state.language,
            label = { tag -> tag?.let { languageName(it) } ?: stringResource(R.string.settings_language_system) },
            onSelect = { onAction(SettingsAction.OnLanguageSelect(it)) },
            onDismiss = { openSheet = null },
        )
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
            message = checkNotNull(state.notificationsError).asString(),
            onRetry = { onAction(SettingsAction.OnRetryNotifications) },
        )
        else -> AppLoadingIndicator(Modifier.padding(AppTheme.spacing.md))
    }
}

/**
 * A section: its header (per the style pack), its content, a divider. An untitled section whose
 * content draws nothing (an app's callout with nothing to say) takes no space and draws no divider.
 */
@Composable
private fun Section(title: String?, content: @Composable () -> Unit) {
    // Measured first; the divider is only composed when the section shows something.
    SubcomposeLayout { constraints ->
        val loose = constraints.copy(minHeight = 0)
        val body = subcompose("body") { Column { if (title != null) AppSectionHeader(title); content() } }.map { it.measure(loose) }
        val bodyHeight = body.sumOf { it.height }
        if (bodyHeight == 0) return@SubcomposeLayout layout(0, 0) {}
        val divider = subcompose("divider") {
            HorizontalDivider(Modifier.padding(top = AppTheme.spacing.sm).testTag(SETTINGS_DIVIDER))
        }.map { it.measure(loose) }
        val all = body + divider
        layout(all.maxOf { it.width }, bodyHeight + divider.sumOf { it.height }) {
            var y = 0
            all.forEach { it.placeRelative(0, y); y += it.height }
        }
    }
}

private const val SETTINGS_DIVIDER = "SettingsDivider"

@Composable
private fun ToggleRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, onValueChange = onToggle, role = Role.Switch)
            .padding(horizontal = AppTheme.style.sizing.screenGutter),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
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

private const val SHEET_THEME = "theme"
private const val SHEET_LANGUAGE = "language"

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

