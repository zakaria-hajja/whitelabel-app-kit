package com.whitelabel.feature.authentication

import com.whitelabel.feature.authentication.data.KtorPasswordResetDataSource
import com.whitelabel.feature.authentication.domain.PasswordResetDataSource
import com.whitelabel.feature.authentication.presentation.forgot.ForgotPasswordViewModel
import com.whitelabel.feature.authentication.presentation.login.LoginViewModel
import com.whitelabel.feature.authentication.presentation.register.RegisterViewModel
import com.whitelabel.feature.authentication.presentation.reset.ResetPasswordViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Sign-in, registration and password reset: added by `auth()`. */
val authenticationModule = module {
    single<PasswordResetDataSource> { KtorPasswordResetDataSource(get()) }
    viewModelOf(::LoginViewModel)
    viewModelOf(::RegisterViewModel)
    viewModelOf(::ForgotPasswordViewModel)
    viewModelOf(::ResetPasswordViewModel)
}
