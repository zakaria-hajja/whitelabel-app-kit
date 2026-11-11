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
import okio.FileSystem
import okio.Path

/**
 * A typed document persisted as JSON at [path], backed by DataStore (atomic writes, one writer).
 * A corrupt file reads as [default] instead of crashing; unknown keys (written by a newer app
 * version) are ignored. Only one store may be open per file at a time, as with any DataStore.
 */
class JsonStore<T>(
    path: Path,
    serializer: KSerializer<T>,
    default: T,
    scope: CoroutineScope,
    fileSystem: FileSystem = SystemFileSystem,
) {
    private val store: DataStore<T> = DataStoreFactory.create(
        storage = OkioStorage(fileSystem, JsonDataSerializer(serializer, default), producePath = { path }),
        corruptionHandler = ReplaceFileCorruptionHandler { default },
        scope = scope,
    )

    val data: Flow<T> get() = store.data

    suspend fun update(transform: (T) -> T) {
        store.updateData { transform(it) }
    }
}

/** The platform's real file system (okio's `FileSystem.SYSTEM`). */
internal expect val SystemFileSystem: FileSystem

private val StoreJson = Json { ignoreUnknownKeys = true }

private class JsonDataSerializer<T>(
    private val serializer: KSerializer<T>,
    override val defaultValue: T,
) : OkioSerializer<T> {
    override suspend fun readFrom(source: BufferedSource): T = try {
        StoreJson.decodeFromString(serializer, source.readUtf8())
    } catch (e: SerializationException) {
        throw CorruptionException("Unreadable JSON store", e)
    } catch (e: IllegalArgumentException) {
        throw CorruptionException("Unreadable JSON store", e)
    }

    override suspend fun writeTo(t: T, sink: BufferedSink) {
        sink.writeUtf8(StoreJson.encodeToString(serializer, t))
    }
}
