package com.whitelabel.apps.showcase

import com.whitelabel.core.shell.AppDefinition
import com.whitelabel.core.shell.appDefinition
import com.whitelabel.core.shell.consent.PrivacyChoicesRow
import com.whitelabel.feature.authentication.auth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import com.whitelabel.feature.catalog.CatalogOptions
import com.whitelabel.feature.catalog.CatalogRoute
import com.whitelabel.feature.catalog.catalog
import com.whitelabel.feature.home.HomeOptions
import com.whitelabel.feature.home.HomeRoute
import com.whitelabel.feature.home.HomeShortcut
import com.whitelabel.feature.home.home
import com.whitelabel.feature.profile.ProfileRoute
import com.whitelabel.feature.profile.profile
import com.whitelabel.feature.settings.SettingsOptions
import com.whitelabel.feature.settings.SettingsRoute
import com.whitelabel.feature.settings.SettingsSection
import com.whitelabel.feature.settings.settings

/** Everything Showcase contains: the reference app, which uses every shared feature. */
fun showcaseDefinition(): AppDefinition = appDefinition {
    // Firebase runs in Debug builds only (app.properties: firebase.enabled=debug); Release runs in off mode.
    firebase()
    auth()
    home(
        HomeOptions(
            shortcuts = listOf(
                HomeShortcut(R.string.showcase_shortcut_catalog, Icons.AutoMirrored.Filled.List, CatalogRoute),
                HomeShortcut(R.string.showcase_shortcut_profile, Icons.Default.Person, ProfileRoute),
                HomeShortcut(R.string.showcase_shortcut_settings, Icons.Default.Settings, SettingsRoute),
            ),
        ),
    )
    // The categories of the backend's seed catalog.
    catalog(CatalogOptions(categories = listOf("Books", "Games", "Music", "Travel")))
    profile()
    settings(SettingsOptions(sections = listOf(SettingsSection(R.string.showcase_settings_firebase) {
        PrivacyChoicesRow()
        RemoteNoteRow()
    })))
    startTab(HomeRoute)
}
