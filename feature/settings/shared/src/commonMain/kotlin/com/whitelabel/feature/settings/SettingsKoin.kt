package com.whitelabel.feature.settings

import com.whitelabel.feature.settings.data.KtorSettingsDataSource
import com.whitelabel.feature.settings.domain.SettingsDataSource
import com.whitelabel.feature.settings.presentation.SettingsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The settings tab: added by `settings()`. */
val settingsModule = module {
    single<SettingsDataSource> { KtorSettingsDataSource(get()) }
    viewModelOf(::SettingsViewModel)
}
