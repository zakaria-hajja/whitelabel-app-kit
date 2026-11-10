package com.whitelabel.apps.showcase

import com.whitelabel.core.shell.AppDefinition
import com.whitelabel.core.shell.appDefinition
import com.whitelabel.core.shell.diagnostics.diagnostics
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Everything Showcase contains. Phase 4 adds the shared features here. */
@Module
@InstallIn(SingletonComponent::class)
object ShowcaseDefinition {
    @Provides
    @Singleton
    fun definition(): AppDefinition = appDefinition {
        diagnostics()
    }
}
