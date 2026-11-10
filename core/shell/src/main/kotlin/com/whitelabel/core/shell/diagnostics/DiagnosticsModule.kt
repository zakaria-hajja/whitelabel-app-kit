package com.whitelabel.core.shell.diagnostics

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface DiagnosticsModule {
    @Binds
    fun healthDataSource(source: KtorHealthDataSource): HealthDataSource
}
