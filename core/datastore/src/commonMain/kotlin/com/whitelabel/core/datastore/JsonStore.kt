package com.whitelabel.core.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.OkioStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okio.BufferedSink
import okio.BufferedSource
import okio.ByteString
import okio.FileSystem
import okio.Path

/**
 * A typed document persisted as JSON at [path], backed by DataStore (atomic writes, one writer).
 * Unknown keys (written by a newer app version) are ignored. Only one store may be open per file at
 * a time, as with any DataStore.
 *
 * A file that doesn't decode is handled by [corruptFile]:
 * - [CorruptFilePolicy.KEEP_COPY_THEN_DEFAULT] (the default) keeps its exact bytes as
 *   `<name>.corrupt` next to it (only the newest copy is kept), then reads as [default], which
 *   DataStore writes over the file. If the copy can't be written, the read fails with
 *   [CorruptStoreException] and nothing is written; the next read tries again.
 * - [CorruptFilePolicy.FAIL] leaves the file as it is and fails every read and update with
 *   [CorruptStoreException].
 *
 * [onCorruptFile] reports what happened. It runs on [scope] and must not throw. No failure from this
 * store, nor any event, carries the file's name, path or content.
 */
class JsonStore<T>(
    private val path: Path,
    serializer: KSerializer<T>,
    private val default: T,
    scope: CoroutineScope,
    private val fileSystem: FileSystem = SystemFileSystem,
    private val corruptFile: CorruptFilePolicy = CorruptFilePolicy.KEEP_COPY_THEN_DEFAULT,
    private val onCorruptFile: (CorruptFileEvent) -> Unit = {},
) {
    private val json = JsonDataSerializer(serializer, default)

    private val store: DataStore<T> = DataStoreFactory.create(
        storage = OkioStorage(fileSystem, json, producePath = { path }),
        corruptionHandler = ReplaceFileCorruptionHandler { handleCorrupt() },
        scope = scope,
    )

    val data: Flow<T> get() = store.data

    suspend fun update(transform: (T) -> T) {
        store.updateData { transform(it) }
    }

    private fun handleCorrupt(): T = when (corruptFile) {
        CorruptFilePolicy.FAIL -> {
            onCorruptFile(CorruptFileEvent.LEFT_IN_PLACE)
            throw CorruptStoreException()
        }
        CorruptFilePolicy.KEEP_COPY_THEN_DEFAULT -> when (keepCorruptCopy(fileSystem, path, json::decodes)) {
            CopyResult.FAILED -> {
                onCorruptFile(CorruptFileEvent.COPY_FAILED)
                throw CorruptStoreException()
            }
            CopyResult.KEPT -> {
                onCorruptFile(CorruptFileEvent.COPY_KEPT)
                default
            }
            // Missing or decodes by now: DataStore re-reads the file under its lock and sees that.
            CopyResult.NOTHING_TO_KEEP -> default
        }
    }
}

/** The platform's real file system (okio's `FileSystem.SYSTEM`). */
internal expect val SystemFileSystem: FileSystem

private val StoreJson = Json { ignoreUnknownKeys = true }

private class JsonDataSerializer<T>(
    private val serializer: KSerializer<T>,
    override val defaultValue: T,
) : OkioSerializer<T> {
    // The CorruptionException never gets the SerializationException as its cause: that message
    // quotes the JSON input, and a failed read must carry no file content.
    override suspend fun readFrom(source: BufferedSource): T =
        (decodeOrNull(source.readByteString()) ?: throw CorruptionException("Unreadable JSON store")).value

    fun decodes(bytes: ByteString): Boolean = decodeOrNull(bytes) != null

    private fun decodeOrNull(bytes: ByteString): Holder<T>? = try {
        Holder(StoreJson.decodeFromString(serializer, bytes.utf8()))
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    override suspend fun writeTo(t: T, sink: BufferedSink) {
        sink.writeUtf8(StoreJson.encodeToString(serializer, t))
    }

    /** A decoded value, so that a `T` that is itself nullable can't be mistaken for "didn't decode". */
    private class Holder<T>(val value: T)
}
