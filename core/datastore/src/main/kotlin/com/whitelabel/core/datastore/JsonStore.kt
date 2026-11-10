package com.whitelabel.core.datastore

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * A typed document persisted as JSON in [file], backed by DataStore (atomic writes, one writer).
 * A corrupt file reads as [default] instead of crashing; unknown keys (written by a newer app
 * version) are ignored. Only one store may be open per file at a time, as with any DataStore.
 */
class JsonStore<T>(
    file: File,
    serializer: KSerializer<T>,
    default: T,
    scope: CoroutineScope,
) {
    private val store: DataStore<T> = DataStoreFactory.create(
        serializer = JsonDataSerializer(serializer, default),
        corruptionHandler = ReplaceFileCorruptionHandler { default },
        scope = scope,
        produceFile = { file },
    )

    val data: Flow<T> get() = store.data

    suspend fun update(transform: (T) -> T) {
        store.updateData { transform(it) }
    }
}

/** `filesDir/datastore/<name>.json`, the conventional location for a [JsonStore] file. */
fun Context.jsonStoreFile(name: String): File = File(filesDir, "datastore/$name.json")

private val StoreJson = Json { ignoreUnknownKeys = true }

private class JsonDataSerializer<T>(
    private val serializer: KSerializer<T>,
    override val defaultValue: T,
) : Serializer<T> {
    override suspend fun readFrom(input: InputStream): T = try {
        StoreJson.decodeFromString(serializer, input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Unreadable JSON store", e)
    } catch (e: IllegalArgumentException) {
        throw CorruptionException("Unreadable JSON store", e)
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        output.write(StoreJson.encodeToString(serializer, t).encodeToByteArray())
    }
}
