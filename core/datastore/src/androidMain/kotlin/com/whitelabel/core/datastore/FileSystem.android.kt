package com.whitelabel.core.datastore

import android.content.Context
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.KSerializer
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath

internal actual val SystemFileSystem: FileSystem = FileSystem.SYSTEM

/** `filesDir/datastore/<name>.json`, the conventional location for a [JsonStore] file on Android. */
fun Context.jsonStorePath(name: String): Path = jsonStoreFile(name).toOkioPath()

/** The same location as a [File], for Android code that works with files. */
fun Context.jsonStoreFile(name: String): File = File(filesDir, "datastore/$name.json")

/** A [JsonStore] at [file], for Android code that works with files (e.g. `noBackupFilesDir`). */
fun <T> JsonStore(file: File, serializer: KSerializer<T>, default: T, scope: CoroutineScope): JsonStore<T> =
    JsonStore(file.toOkioPath(), serializer, default, scope)
