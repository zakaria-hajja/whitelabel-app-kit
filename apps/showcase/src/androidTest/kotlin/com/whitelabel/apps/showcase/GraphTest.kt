package com.whitelabel.apps.showcase

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.whitelabel.core.testing.android.FreshAppRule
import com.whitelabel.feature.catalog.CatalogOptions
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.GlobalContext
import org.koin.core.parameter.parametersOf

/**
 * Every ViewModel of the app resolves from the real graph. Koin finds a missing binding only
 * when a screen opens, so this test opens none and asks for each one.
 * The classes are internal to their modules, so they are named here.
 */
class GraphTest {

    @get:Rule val fresh = FreshAppRule()

    private val viewModels = listOf(
        "com.whitelabel.core.shell.ShellViewModel",
        "com.whitelabel.core.shell.diagnostics.BrandPreviewViewModel",
        "com.whitelabel.feature.authentication.presentation.login.LoginViewModel",
        "com.whitelabel.feature.authentication.presentation.register.RegisterViewModel",
        "com.whitelabel.feature.authentication.presentation.forgot.ForgotPasswordViewModel",
        "com.whitelabel.feature.authentication.presentation.reset.ResetPasswordViewModel",
        "com.whitelabel.feature.settings.presentation.SettingsViewModel",
        "com.whitelabel.feature.home.presentation.HomeViewModel",
        "com.whitelabel.feature.catalog.presentation.list.CatalogListViewModel",
        "com.whitelabel.feature.catalog.presentation.detail.CatalogDetailViewModel",
        "com.whitelabel.feature.profile.presentation.profile.ProfileViewModel",
        "com.whitelabel.feature.profile.presentation.password.ChangePasswordViewModel",
    )

    @Test fun everyViewModelResolves() {
        val koin = GlobalContext.get()
        viewModels.forEach { name ->
            val type = Class.forName(name).kotlin
            val parameters = if (name.endsWith("CatalogListViewModel")) parametersOf(CatalogOptions()) else parametersOf(SavedStateHandle())
            val viewModel = koin.get<Any>(type, null) { parameters }
            assertTrue(name, viewModel is ViewModel)
        }
    }
}
