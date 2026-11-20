package com.whitelabel.core.datastore

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import okio.FileSystem

/** The corrupt copy on the platform's real file system (JVM and iOS simulator), not a fake. */
class SystemFileCorruptCopyTest {

    private val fileSystem = SystemFileSystem
    private val dir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "jsonstore-${Random.nextLong().toULong()}"
    private val file = dir / "doc.json"
    private val copy = dir / "doc.json.corrupt"

    @AfterTest fun cleanUp() {
        fileSystem.deleteRecursively(dir, mustExist = false)
    }

    private suspend fun readFreshStore(): Doc {
        val job = Job()
        val store = JsonStore(file, Doc.serializer(), Doc(), CoroutineScope(Dispatchers.Default + job), fileSystem)
        return try {
            store.data.first()
        } finally {
            job.cancelAndJoin()
        }
    }

    private fun corrupt(bytes: ByteString) {
        fileSystem.createDirectories(dir)
        fileSystem.write(file) { write(bytes) }
    }

    private fun bytes(path: okio.Path): ByteString = fileSystem.read(path) { readByteString() }

    @Test fun keepsTheOriginalAndWritesTheDefault() = runTest {
        val original = "{\"items\":[\"a".encodeUtf8().toByteArray().plus(0xFF.toByte()).toByteString()
        corrupt(original)

        assertThat(readFreshStore()).isEqualTo(Doc())
        assertThat(bytes(copy)).isEqualTo(original)
        assertThat(bytes(file).utf8()).isEqualTo("{}")
    }

    @Test fun aNewerCorruptionReplacesTheCopy() = runTest {
        corrupt("{first".encodeUtf8())
        readFreshStore()
        corrupt("{second".encodeUtf8())
        readFreshStore()

        assertThat(bytes(copy).utf8()).isEqualTo("{second")
    }
}
