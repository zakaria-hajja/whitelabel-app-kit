package com.whitelabel.apps.globex.notes

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import com.whitelabel.core.datastore.JsonStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class NotesStoreTest {

    @TempDir
    lateinit var dir: File

    private suspend fun <R> withStore(block: suspend (JsonNotesStore) -> R): R {
        val job = Job()
        var time = 100L
        var id = 0
        val json = JsonStore(File(dir, "notes.json"), ListSerializer(Note.serializer()), emptyList(), CoroutineScope(Dispatchers.IO + job))
        return try {
            block(JsonNotesStore(json, clock = { time++ }, newId = { "id${id++}" }))
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test fun `newest note comes first and survives reopening`() = runTest {
        withStore { store ->
            store.add("first")
            store.add("second")
        }

        val notes = withStore { it.notes.first() }

        assertThat(notes.map { it.text }).containsExactly("second", "first")
        assertThat(notes.map { it.id }).containsExactly("id1", "id0")
        assertThat(notes.first().createdAtEpochMillis).isEqualTo(101L)
    }

    @Test fun `delete removes only that note`() = runTest {
        val remaining = withStore { store ->
            store.add("keep")
            store.add("drop")
            store.delete("id1")
            store.notes.first()
        }

        assertThat(remaining.map { it.text }).containsExactly("keep")
    }
}
