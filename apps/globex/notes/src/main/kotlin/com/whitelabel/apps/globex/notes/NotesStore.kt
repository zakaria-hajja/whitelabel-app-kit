package com.whitelabel.apps.globex.notes

import com.whitelabel.core.datastore.JsonStore
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
internal data class Note(val id: String, val text: String, val createdAtEpochMillis: Long)

internal interface NotesStore {
    /** Newest first. */
    val notes: Flow<List<Note>>
    suspend fun add(text: String)
    suspend fun delete(id: String)
}

internal class JsonNotesStore(
    private val store: JsonStore<List<Note>>,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : NotesStore {
    override val notes: Flow<List<Note>> get() = store.data

    override suspend fun add(text: String) {
        val note = Note(id = newId(), text = text, createdAtEpochMillis = clock())
        store.update { listOf(note) + it }
    }

    override suspend fun delete(id: String) {
        store.update { notes -> notes.filterNot { it.id == id } }
    }
}
