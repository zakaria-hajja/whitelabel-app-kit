package com.whitelabel.feature.profile

import com.whitelabel.feature.profile.data.KtorPasswordDataSource
import com.whitelabel.feature.profile.domain.PasswordDataSource
import com.whitelabel.feature.profile.presentation.password.ChangePasswordViewModel
import com.whitelabel.feature.profile.presentation.profile.ProfileViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The profile and password change: added by `profile()`. */
val profileModule = module {
    single<PasswordDataSource> { KtorPasswordDataSource(get()) }
    viewModelOf(::ProfileViewModel)
    viewModelOf(::ChangePasswordViewModel)
}
