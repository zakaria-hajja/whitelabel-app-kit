package com.whitelabel.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.navigation.compose.composable
import com.whitelabel.core.shell.AppDefinitionBuilder
import com.whitelabel.core.shell.FeatureEntry
import com.whitelabel.core.shell.Tab
import com.whitelabel.core.shell.diagnostics.DiagnosticsRoute
import com.whitelabel.feature.settings.presentation.SettingsRoot
import kotlinx.serialization.Serializable

/**
 * [showNotifications] shows the notification toggles (signed in only). [languages] are the BCP 47
 * tags the app is translated into, offered as the app's language (filtered by the backend's list).
 */
data class SettingsOptions(
    val showNotifications: Boolean = true,
    val languages: List<String> = listOf("en", "fr"),
)

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
                composable<SettingsRoute> {
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
