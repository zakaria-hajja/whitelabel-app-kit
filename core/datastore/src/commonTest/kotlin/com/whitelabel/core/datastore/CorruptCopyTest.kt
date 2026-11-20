package com.whitelabel.core.datastore

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import kotlin.test.Test
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.fakefilesystem.FakeFileSystem

class CorruptCopyTest {

    private val fake = FakeFileSystem()
    private val file = "/data/doc.json".toPath().also { fake.createDirectories(it.parent!!) }
    private val copy = "/data/doc.json.corrupt".toPath()
    private val tmp = "/data/doc.json.corrupt.tmp".toPath()
    private val decodes: (ByteString) -> Boolean = { it.utf8().startsWith("{\"ok") }

    private fun FileSystem.bytes(path: Path): ByteString = read(path) { readByteString() }

    @Test fun `copy path sits next to the file`() {
        assertThat(file.corruptCopy()).isEqualTo(copy)
    }

    @Test fun `copies the exact bytes`() {
        val original = "{bad".encodeUtf8().toByteArray().plus(0xFF.toByte()).toByteString()
        fake.write(file) { write(original) }

        assertThat(keepCorruptCopy(fake, file, decodes)).isEqualTo(CorruptFileEvent.COPY_KEPT)
        assertThat(fake.bytes(copy)).isEqualTo(original)
        assertThat(fake.bytes(file)).isEqualTo(original)
        assertThat(fake.exists(tmp)).isFalse()
    }

    @Test fun `a newer copy replaces the older one`() {
        fake.write(copy) { writeUtf8("old") }
        fake.write(file) { writeUtf8("{new") }

        assertThat(keepCorruptCopy(fake, file, decodes)).isEqualTo(CorruptFileEvent.COPY_KEPT)
        assertThat(fake.bytes(copy).utf8()).isEqualTo("{new")
    }

    @Test fun `skips a file that decodes`() {
        fake.write(file) { writeUtf8("""{"ok":1}""") }

        assertThat(keepCorruptCopy(fake, file, decodes)).isNull()
        assertThat(fake.exists(copy)).isFalse()
    }

    @Test fun `skips a missing file`() {
        assertThat(keepCorruptCopy(fake, file, decodes)).isNull()
        assertThat(fake.exists(copy)).isFalse()
    }

    @Test fun `copy failure returns COPY_FAILED and keeps the original`() {
        fake.write(file) { writeUtf8("{bad") }
        val failing = object : ForwardingFileSystem(fake) {
            override fun sink(file: Path, mustCreate: Boolean): Sink {
                if (file.name.endsWith(".corrupt.tmp")) throw IOException("disk full")
                return super.sink(file, mustCreate)
            }
        }

        assertThat(keepCorruptCopy(failing, file, decodes)).isEqualTo(CorruptFileEvent.COPY_FAILED)
        assertThat(fake.bytes(file).utf8()).isEqualTo("{bad")
        assertThat(fake.exists(copy)).isFalse()
        assertThat(fake.exists(tmp)).isFalse()
    }

    @Test fun `CorruptStoreException carries nothing`() {
        val e = CorruptStoreException()

        assertThat(e.message).isEqualTo("JSON store unreadable")
        assertThat(e.cause).isNull()
    }
}
