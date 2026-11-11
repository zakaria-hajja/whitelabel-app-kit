package com.whitelabel.apps.showcase

import com.whitelabel.core.shell.AppDefinition
import com.whitelabel.core.shell.appDefinition
import com.whitelabel.feature.authentication.auth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import com.whitelabel.feature.home.HomeOptions
import com.whitelabel.feature.home.HomeRoute
import com.whitelabel.feature.home.HomeShortcut
import com.whitelabel.feature.home.home
import com.whitelabel.feature.profile.ProfileRoute
import com.whitelabel.feature.profile.profile
import com.whitelabel.feature.settings.SettingsRoute
import com.whitelabel.feature.settings.settings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Everything Showcase contains: the reference app, which uses every shared feature. */
@Module
@InstallIn(SingletonComponent::class)
object ShowcaseDefinition {
    @Provides
    @Singleton
    fun definition(): AppDefinition = appDefinition {
        auth()
        home(
            HomeOptions(
                shortcuts = listOf(
                    HomeShortcut(R.string.showcase_shortcut_profile, Icons.Default.Person, ProfileRoute),
                    HomeShortcut(R.string.showcase_shortcut_settings, Icons.Default.Settings, SettingsRoute),
                ),
            ),
        )
        profile()
        settings()
        startTab(HomeRoute)
    }
}
