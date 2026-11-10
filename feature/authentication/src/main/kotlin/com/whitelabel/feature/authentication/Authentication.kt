package com.whitelabel.feature.authentication

import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import com.whitelabel.core.shell.AppDefinitionBuilder
import com.whitelabel.core.shell.FeatureEntry
import com.whitelabel.core.shell.Gate
import com.whitelabel.feature.authentication.presentation.forgot.ForgotPasswordRoot
import com.whitelabel.feature.authentication.presentation.login.LoginRoot
import com.whitelabel.feature.authentication.presentation.register.RegisterRoot
import com.whitelabel.feature.authentication.presentation.reset.ResetPasswordRoot
import kotlinx.serialization.Serializable

/** What the sign-in screens offer besides signing in. */
data class AuthOptions(
    val allowRegistration: Boolean = true,
    val allowPasswordReset: Boolean = true,
)

@Serializable internal data object LoginRoute

@Serializable internal data object RegisterRoute

@Serializable internal data object ForgotPasswordRoute

/** Opened from the reset email: `<scheme>://reset-password?token=…` (an optional argument becomes a query parameter). */
@Serializable internal data class ResetPasswordRoute(val token: String = "")

/**
 * Makes the whole app sign-in only, with email and password sign-in, registration and password
 * reset. For the reset link to open the app, the tenant's `passwordReset.linkBaseUrl` must be
 * `<app.deepLinkScheme>://reset-password`.
 */
fun AppDefinitionBuilder.auth(options: AuthOptions = AuthOptions()) {
    add(
        FeatureEntry(
            id = "auth",
            gate = Gate(LoginRoute),
            routes = { scope ->
                val navigator = scope.navigator
                composable<LoginRoute> {
                    LoginRoot(
                        options = options,
                        onCreateAccount = { navigator.navigate(RegisterRoute) },
                        onForgotPassword = { navigator.navigate(ForgotPasswordRoute) },
                    )
                }
                composable<RegisterRoute> { RegisterRoot(onBack = navigator::back) }
                composable<ForgotPasswordRoute> { ForgotPasswordRoot(onBack = navigator::back) }
                composable<ResetPasswordRoute>(
                    deepLinks = listOf(navDeepLink<ResetPasswordRoute>(basePath = "${scope.deepLinkScheme}://reset-password")),
                ) {
                    ResetPasswordRoot(
                        onBackToSignIn = navigator::back,
                        onRequestNewLink = { navigator.navigate(ForgotPasswordRoute) },
                    )
                }
            },
        ),
    )
}
