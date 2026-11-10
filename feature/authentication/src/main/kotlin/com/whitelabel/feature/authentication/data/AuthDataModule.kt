package com.whitelabel.feature.authentication.data

import com.whitelabel.feature.authentication.domain.PasswordResetDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AuthDataModule {
    @Binds abstract fun passwordReset(impl: KtorPasswordResetDataSource): PasswordResetDataSource
}
