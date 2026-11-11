package com.whitelabel.core.shell.brand

import com.whitelabel.core.network.EngineOverride
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.engine.HttpClientEngine
import java.util.Optional
import javax.inject.Qualifier

/** True when a debug module (the mock backend) replaced the HTTP engine. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class UsingMockApi

/** Lets `:core:mockapi` replace the engine when it is on the classpath; absent, OkHttp is used. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class EngineModule {
    @BindsOptionalOf
    abstract fun engineOverride(): EngineOverride
}

internal fun selectEngine(override: Optional<EngineOverride>, default: () -> HttpClientEngine): HttpClientEngine =
    if (override.isPresent) override.get().engine() else default()
