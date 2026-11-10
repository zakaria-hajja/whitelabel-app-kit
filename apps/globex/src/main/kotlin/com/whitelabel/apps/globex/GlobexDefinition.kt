package com.whitelabel.apps.globex

import com.whitelabel.apps.globex.notes.NotesRoute
import com.whitelabel.apps.globex.notes.notes
import com.whitelabel.core.shell.AppDefinition
import com.whitelabel.core.shell.appDefinition
import com.whitelabel.core.shell.diagnostics.diagnostics
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Everything Globex contains: its own notes feature plus shared diagnostics. */
@Module
@InstallIn(SingletonComponent::class)
object GlobexDefinition {
    @Provides
    @Singleton
    fun definition(): AppDefinition = appDefinition {
        notes()
        diagnostics()
        startTab(NotesRoute)
    }
}
