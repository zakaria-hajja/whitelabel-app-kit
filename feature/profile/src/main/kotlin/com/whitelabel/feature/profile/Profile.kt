package com.whitelabel.feature.profile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import com.whitelabel.core.shell.AppDefinitionBuilder
import com.whitelabel.core.shell.FeatureEntry
import com.whitelabel.core.shell.screen
import com.whitelabel.core.shell.Tab
import com.whitelabel.feature.profile.presentation.password.ChangePasswordRoot
import com.whitelabel.feature.profile.presentation.profile.ProfileRoot
import kotlinx.serialization.Serializable

/** [allowPasswordChange] shows "Change password" on the profile screen. */
data class ProfileOptions(val allowPasswordChange: Boolean = true)

/** Root of the profile tab; public so an app can name it (start tab, home shortcut). */
@Serializable
data object ProfileRoute

@Serializable
internal data object ChangePasswordRoute

/**
 * Adds the profile tab: the signed-in user's email and names, and a password change. Needs
 * `auth()`. Its flag key is `profile`, which the backend never enforces: list it in
 * `defaults.features` (or switch it on remotely) for the tab to show.
 */
fun AppDefinitionBuilder.profile(options: ProfileOptions = ProfileOptions()) {
    add(
        FeatureEntry(
            id = "profile",
            flagKey = "profile",
            requiresSignIn = true,
            tab = Tab(ProfileRoute, R.string.profile_tab, Icons.Default.Person, routeId = ProfileRoutes.ROOT),
            modules = listOf(profileModule),
            routes = { scope ->
                screen<ProfileRoute> {
                    ProfileRoot(options = options, onChangePassword = { scope.navigator.navigate(ChangePasswordRoute) })
                }
                if (options.allowPasswordChange) {
                    screen<ChangePasswordRoute> { ChangePasswordRoot(onBack = scope.navigator::back) }
                }
            },
        ),
    )
}
