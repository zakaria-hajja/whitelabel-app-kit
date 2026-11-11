package com.whitelabel.feature.settings

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.filled.Settings
import com.whitelabel.core.shell.AppDefinitionBuilder
import com.whitelabel.core.shell.FeatureEntry
import com.whitelabel.core.shell.screen
import com.whitelabel.core.shell.Tab
import com.whitelabel.core.shell.diagnostics.DiagnosticsRoute
import com.whitelabel.feature.settings.presentation.SettingsRoot
import kotlinx.serialization.Serializable

/**
 * [showNotifications] shows the notification toggles (signed in only). [languages] are the BCP 47
 * tags the app is translated into, offered as the app's language (filtered by the backend's list);
 * an empty list hides the language row. [sections] are the app's own, shown above the shared ones.
 * [aboutNote] is a line under the version in About (e.g. "Your data stays on this phone.").
 */
data class SettingsOptions(
    val showNotifications: Boolean = true,
    val languages: List<String> = listOf("en", "fr"),
    val sections: List<SettingsSection> = emptyList(),
    @StringRes val aboutNote: Int? = null,
)

/**
 * An app's own settings section, shown above the shared ones in list order. A null [title] draws no
 * header (for a callout). [content] is composed inside the settings screen, so it may use
 * `hiltViewModel()` for its own state; build its rows from [SettingsValueRow] and [SettingsChoiceSheet].
 */
class SettingsSection(@StringRes val title: Int?, val content: @Composable () -> Unit)

/** Root of the settings tab; public so an app can name it (start tab, home shortcut). */
@Serializable
data object SettingsRoute

/** Adds the settings tab: theme, language, notifications, about, legal links, sign out. Works without `auth()`. */
fun AppDefinitionBuilder.settings(options: SettingsOptions = SettingsOptions()) {
    add(
        FeatureEntry(
            id = "settings",
            tab = Tab(SettingsRoute, R.string.settings_tab, Icons.Default.Settings),
            routes = { scope ->
                screen<SettingsRoute> {
                    SettingsRoot(
                        options = options,
                        onOpenUrl = scope.navigator::openUrl,
                        onOpenDiagnostics = { scope.navigator.navigate(DiagnosticsRoute) },
                    )
                }
            },
        ),
    )
}
