package com.whitelabel.app.diagnostics

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
