package com.whitelabel.feature.settings.data

import com.whitelabel.feature.settings.domain.SettingsDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SettingsDataModule {
    @Binds abstract fun settings(impl: KtorSettingsDataSource): SettingsDataSource
}
