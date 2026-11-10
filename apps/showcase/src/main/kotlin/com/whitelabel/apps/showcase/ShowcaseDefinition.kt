package com.whitelabel.apps.showcase

import com.whitelabel.core.shell.AppDefinition
import com.whitelabel.core.shell.appDefinition
import com.whitelabel.core.shell.diagnostics.diagnostics
import com.whitelabel.feature.authentication.auth
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Everything Showcase contains: the reference app, which uses every shared feature. */
@Module
@InstallIn(SingletonComponent::class)
object ShowcaseDefinition {
    @Provides
    @Singleton
    fun definition(): AppDefinition = appDefinition {
        auth()
        diagnostics()
    }
}
