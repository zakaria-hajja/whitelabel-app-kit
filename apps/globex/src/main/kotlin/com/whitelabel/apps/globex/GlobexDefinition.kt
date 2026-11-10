package com.whitelabel.apps.globex

import com.whitelabel.apps.globex.notes.NotesRoute
import com.whitelabel.apps.globex.notes.notes
import com.whitelabel.core.shell.AppDefinition
import com.whitelabel.core.shell.appDefinition
import com.whitelabel.core.shell.diagnostics.diagnostics
import com.whitelabel.feature.authentication.auth
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Everything Globex contains: sign-in, its own notes feature, and shared diagnostics. */
@Module
@InstallIn(SingletonComponent::class)
object GlobexDefinition {
    @Provides
    @Singleton
    fun definition(): AppDefinition = appDefinition {
        auth()
        notes()
        diagnostics()
        startTab(NotesRoute)
    }
}
