package com.whitelabel.core.datastore

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEmpty
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.fail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import okio.FileHandle
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.fakefilesystem.FakeFileSystem

@Serializable
data class Doc(val items: List<String> = emptyList(), val count: Int = 0)

class JsonStoreTest {

    private val fake = FakeFileSystem()

    /**
     * Fails every write to a file whose name ends with [failSinksEndingWith], like a full disk. It
     * covers `sink` (our copy) and `openReadWrite` (DataStore's own write).
     */
    private val fileSystem = object : ForwardingFileSystem(fake) {
        var failSinksEndingWith: String? = null

        private fun check(file: Path) {
            failSinksEndingWith?.let { if (file.name.endsWith(it)) throw IOException("disk full") }
        }

        override fun sink(file: Path, mustCreate: Boolean): Sink {
            check(file)
            return super.sink(file, mustCreate)
        }

        override fun openReadWrite(file: Path, mustCreate: Boolean, mustExist: Boolean): FileHandle {
            check(file)
            return super.openReadWrite(file, mustCreate, mustExist)
        }
    }

    private val file = "/data/doc.json".toPath().also { fake.createDirectories(it.parent!!) }
    private val copy = "/data/doc.json.corrupt".toPath()
    private val events = mutableListOf<CorruptFileEvent>()

    private fun FileSystem.text(path: Path): String = read(path) { readUtf8() }

    /** Runs [block] against a store whose scope is cancelled afterwards, so another store may open the file. */
    private suspend fun <R> withStore(
        policy: CorruptFilePolicy = CorruptFilePolicy.KEEP_COPY_THEN_DEFAULT,
        block: suspend (JsonStore<Doc>) -> R,
    ): R {
        val job = Job()
        val store = JsonStore(file, Doc.serializer(), Doc(), CoroutineScope(Dispatchers.Default + job), fileSystem, policy) {
            events += it
        }
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

    @Test fun `corrupt file is kept as a copy and reads as default`() = runTest {
        fake.write(file) { writeUtf8("""{"items":["secret"""") }

        assertThat(withStore { it.data.first() }).isEqualTo(Doc())
        assertThat(fake.text(copy)).isEqualTo("""{"items":["secret"""")
        assertThat(fake.text(file)).isEqualTo("{}")
        assertThat(events).containsOnly(CorruptFileEvent.COPY_KEPT)
    }

    @Test fun `update on a corrupt file keeps the copy first`() = runTest {
        fake.write(file) { writeUtf8("{not json") }

        withStore { store -> store.update { it.copy(count = 5) } }

        assertThat(fake.text(copy)).isEqualTo("{not json")
        assertThat(withStore { it.data.first() }).isEqualTo(Doc(count = 5))
    }

    @Test fun `a valid file never gets a copy`() = runTest {
        fake.write(file) { writeUtf8("""{"count":1}""") }

        withStore { store ->
            store.data.first()
            store.update { it.copy(count = 2) }
        }

        assertThat(fake.exists(copy)).isFalse()
        assertThat(events).isEmpty()
    }

    @Test fun `copy failure writes nothing and fails the read`() = runTest {
        fake.write(file) { writeUtf8("{not json") }
        fileSystem.failSinksEndingWith = ".corrupt.tmp"

        withStore { store ->
            assertFailsWith<CorruptStoreException> { store.data.first() }
            assertThat(fake.text(file)).isEqualTo("{not json")
            assertThat(fake.exists(copy)).isFalse()
            assertThat(events).containsOnly(CorruptFileEvent.COPY_FAILED)

            fileSystem.failSinksEndingWith = null
            assertThat(store.data.first()).isEqualTo(Doc())
        }
        assertThat(fake.text(copy)).isEqualTo("{not json")
    }

    @Test fun `FAIL leaves the file alone`() = runTest {
        fake.write(file) { writeUtf8("{not json") }

        withStore(CorruptFilePolicy.FAIL) { store ->
            assertFailsWith<CorruptStoreException> { store.data.first() }
            assertFailsWith<CorruptStoreException> { store.update { it.copy(count = 1) } }
        }

        assertThat(fake.text(file)).isEqualTo("{not json")
        assertThat(fake.exists(copy)).isFalse()
        assertThat(events).isNotEmpty()
        assertThat(events.all { it == CorruptFileEvent.LEFT_IN_PLACE }).isTrue()
    }

    @Test fun `no failure carries file content`() = runTest {
        val secret = """{"note":"secret"""
        val failures = buildList {
            fake.write(file) { writeUtf8(secret) }
            add(withStore(CorruptFilePolicy.FAIL) { store -> failureOf { store.data.first() } })

            fileSystem.failSinksEndingWith = ".corrupt.tmp"
            add(withStore { store -> failureOf { store.data.first() } })

            fileSystem.failSinksEndingWith = "doc.json.tmp"
            add(withStore { store -> failureOf { store.data.first() } })
        }

        assertThat(fake.text(copy)).isEqualTo(secret)
        failures.forEach { failure ->
            failure.everything().forEach { text -> assertThat(text.contains("secret")).isFalse() }
        }
    }

    @Test fun `unknown keys from a newer version are ignored`() = runTest {
        fake.write(file) { writeUtf8("""{"items":["x"],"count":2,"addedLater":true}""") }

        assertThat(withStore { it.data.first() }).isEqualTo(Doc(items = listOf("x"), count = 2))
    }

    private suspend fun failureOf(block: suspend () -> Unit): Throwable {
        try {
            block()
        } catch (e: Throwable) {
            return e
        }
        fail("expected a failure")
    }

    /** Every message and `toString()` in this throwable, its causes and its suppressed exceptions. */
    private fun Throwable.everything(): List<String> =
        listOfNotNull(message, toString()) + (cause?.everything().orEmpty()) + suppressedExceptions.flatMap { it.everything() }
}
