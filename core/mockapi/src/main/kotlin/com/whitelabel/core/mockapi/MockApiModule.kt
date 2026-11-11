package com.whitelabel.core.mockapi

import com.whitelabel.core.network.EngineOverride
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the in-memory backend as the app's HTTP engine. Being on the classpath is the switch: the
 * app plugin adds this module to debug builds only, behind `-Pwhitelabel.mockApi=true`.
 */
@Module
@InstallIn(SingletonComponent::class)
object MockApiModule {
    @Provides @Singleton
    fun fakeBackend(): FakeBackend = FakeBackend()

    @Provides @Singleton
    fun engineOverride(backend: FakeBackend): EngineOverride = EngineOverride { backend.engine }
}
