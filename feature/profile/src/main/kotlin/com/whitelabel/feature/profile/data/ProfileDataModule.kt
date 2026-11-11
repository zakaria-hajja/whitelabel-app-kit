package com.whitelabel.feature.profile.data

import com.whitelabel.feature.profile.domain.PasswordDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ProfileDataModule {
    @Binds abstract fun passwords(impl: KtorPasswordDataSource): PasswordDataSource
}
