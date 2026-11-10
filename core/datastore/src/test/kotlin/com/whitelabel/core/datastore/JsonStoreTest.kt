package com.whitelabel.core.datastore

import assertk.assertThat
import assertk.assertions.isEqualTo
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

@Serializable
data class Doc(val items: List<String> = emptyList(), val count: Int = 0)

class JsonStoreTest {

    @TempDir
    lateinit var dir: File

    private val file get() = File(dir, "doc.json")

    /** Runs [block] against a store whose scope is cancelled afterwards, so another store may open the file. */
    private suspend fun <R> withStore(block: suspend (JsonStore<Doc>) -> R): R {
        val job = Job()
        val store = JsonStore(file, Doc.serializer(), Doc(), CoroutineScope(Dispatchers.IO + job))
        return try {
            block(store)
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test fun `empty file yields default`() = runTest {
        assertThat(withStore { it.data.first() }).isEqualTo(Doc())
    }

    @Test fun `update persists and survives a new store on the same file`() = runTest {
        withStore { store -> store.update { it.copy(items = it.items + "a", count = 1) } }

        assertThat(withStore { it.data.first() }).isEqualTo(Doc(items = listOf("a"), count = 1))
    }

    @Test fun `corrupt file yields default instead of crashing`() = runTest {
        file.writeText("{not json")

        assertThat(withStore { it.data.first() }).isEqualTo(Doc())
    }

    @Test fun `unknown keys from a newer version are ignored`() = runTest {
        file.writeText("""{"items":["x"],"count":2,"addedLater":true}""")

        assertThat(withStore { it.data.first() }).isEqualTo(Doc(items = listOf("x"), count = 2))
    }
}
