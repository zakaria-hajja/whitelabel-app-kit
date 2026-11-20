package com.whitelabel.core.datastore

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import kotlin.test.Test
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import okio.FileHandle
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

        assertThat(keepCorruptCopy(fake, file, decodes)).isEqualTo(CopyResult.KEPT)
        assertThat(fake.bytes(copy)).isEqualTo(original)
        assertThat(fake.bytes(file)).isEqualTo(original)
        assertThat(fake.exists(tmp)).isFalse()
    }

    @Test fun `a newer copy replaces the older one`() {
        fake.write(copy) { writeUtf8("old") }
        fake.write(file) { writeUtf8("{new") }

        assertThat(keepCorruptCopy(fake, file, decodes)).isEqualTo(CopyResult.KEPT)
        assertThat(fake.bytes(copy).utf8()).isEqualTo("{new")
    }

    @Test fun `a stale tmp from an earlier crash doesn't block or leak into the copy`() {
        fake.write(tmp) { writeUtf8("stale and longer than the new bytes") }
        fake.write(file) { writeUtf8("{new") }

        assertThat(keepCorruptCopy(fake, file, decodes)).isEqualTo(CopyResult.KEPT)
        assertThat(fake.bytes(copy).utf8()).isEqualTo("{new")
        assertThat(fake.exists(tmp)).isFalse()
    }

    @Test fun `skips a file that decodes`() {
        fake.write(file) { writeUtf8("""{"ok":1}""") }

        assertThat(keepCorruptCopy(fake, file, decodes)).isEqualTo(CopyResult.NOTHING_TO_KEEP)
        assertThat(fake.exists(copy)).isFalse()
    }

    @Test fun `skips a missing file`() {
        assertThat(keepCorruptCopy(fake, file, decodes)).isEqualTo(CopyResult.NOTHING_TO_KEEP)
        assertThat(fake.exists(copy)).isFalse()
    }

    @Test fun `copy failure returns COPY_FAILED and keeps the original`() {
        fake.write(file) { writeUtf8("{bad") }
        val failing = object : ForwardingFileSystem(fake) {
            override fun sink(file: Path, mustCreate: Boolean): Sink {
                if (file.name.endsWith(".corrupt.tmp")) throw IOException("disk full")
                return super.sink(file, mustCreate)
            }

            override fun openReadWrite(file: Path, mustCreate: Boolean, mustExist: Boolean): FileHandle {
                if (file.name.endsWith(".corrupt.tmp")) throw IOException("disk full")
                return super.openReadWrite(file, mustCreate, mustExist)
            }
        }

        assertThat(keepCorruptCopy(failing, file, decodes)).isEqualTo(CopyResult.FAILED)
        assertThat(fake.bytes(file).utf8()).isEqualTo("{bad")
        assertThat(fake.exists(copy)).isFalse()
        assertThat(fake.exists(tmp)).isFalse()
    }

    @Test fun `the copy is flushed to disk before it takes the copy's name`() {
        fake.write(file) { writeUtf8("{bad") }
        val log = mutableListOf<String>()
        val recording = object : ForwardingFileSystem(fake) {
            override fun sink(file: Path, mustCreate: Boolean): Sink {
                log += "sink ${file.name}"
                return super.sink(file, mustCreate)
            }

            override fun openReadWrite(file: Path, mustCreate: Boolean, mustExist: Boolean): FileHandle {
                log += "openReadWrite ${file.name}"
                return FlushRecordingHandle(super.openReadWrite(file, mustCreate, mustExist)) { log += "flush ${file.name}" }
            }

            override fun atomicMove(source: Path, target: Path) {
                log += "atomicMove ${source.name} -> ${target.name}"
                super.atomicMove(source, target)
            }
        }

        assertThat(keepCorruptCopy(recording, file, decodes)).isEqualTo(CopyResult.KEPT)
        assertThat(log).containsExactly(
            "openReadWrite doc.json.corrupt.tmp",
            "flush doc.json.corrupt.tmp",
            "atomicMove doc.json.corrupt.tmp -> doc.json.corrupt",
        )
        assertThat(fake.bytes(copy).utf8()).isEqualTo("{bad")
    }

    @Test fun `CorruptStoreException carries nothing`() {
        val e = CorruptStoreException()

        assertThat(e.message).isEqualTo("JSON store unreadable")
        assertThat(e.cause).isNull()
    }

    /** Delegates to [delegate] and calls [onFlush] when the handle is flushed (fsync on the JVM). */
    private class FlushRecordingHandle(private val delegate: FileHandle, private val onFlush: () -> Unit) : FileHandle(readWrite = true) {
        override fun protectedRead(fileOffset: Long, array: ByteArray, arrayOffset: Int, byteCount: Int): Int =
            delegate.read(fileOffset, array, arrayOffset, byteCount)

        override fun protectedWrite(fileOffset: Long, array: ByteArray, arrayOffset: Int, byteCount: Int) =
            delegate.write(fileOffset, array, arrayOffset, byteCount)

        override fun protectedFlush() {
            delegate.flush()
            onFlush()
        }

        override fun protectedResize(size: Long) = delegate.resize(size)

        override fun protectedSize(): Long = delegate.size()

        override fun protectedClose() = delegate.close()
    }
}
