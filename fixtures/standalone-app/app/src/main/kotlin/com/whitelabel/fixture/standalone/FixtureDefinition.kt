package com.whitelabel.fixture.standalone

import com.whitelabel.core.shell.AppDefinition
import com.whitelabel.core.shell.appDefinition
import com.whitelabel.core.shell.diagnostics.diagnostics
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** The fixture uses only diagnostics, which works without a backend. */
@Module
@InstallIn(SingletonComponent::class)
object FixtureDefinition {
    @Provides
    @Singleton
    fun definition(): AppDefinition = appDefinition {
        diagnostics()
    }
}
