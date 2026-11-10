package com.whitelabel.apps.globex.notes

import android.content.Context
import com.whitelabel.core.datastore.JsonStore
import com.whitelabel.core.datastore.jsonStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.builtins.ListSerializer

@Module
@InstallIn(SingletonComponent::class)
internal object NotesModule {
    @Provides
    @Singleton
    fun notesStore(@ApplicationContext context: Context): NotesStore = JsonNotesStore(
        JsonStore(
            file = context.jsonStoreFile("notes"),
            serializer = ListSerializer(Note.serializer()),
            default = emptyList(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        ),
    )
}
