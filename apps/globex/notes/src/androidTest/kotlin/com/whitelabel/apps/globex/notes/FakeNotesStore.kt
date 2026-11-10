package com.whitelabel.apps.globex.notes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal class FakeNotesStore : NotesStore {
    val added = mutableListOf<String>()
    private val state = MutableStateFlow<List<Note>>(emptyList())
    override val notes: Flow<List<Note>> = state

    override suspend fun add(text: String) {
        added += text
        state.update { listOf(Note(id = "n${it.size}", text = text, createdAtEpochMillis = 0)) + it }
    }

    override suspend fun delete(id: String) {
        state.update { notes -> notes.filterNot { it.id == id } }
    }
}
