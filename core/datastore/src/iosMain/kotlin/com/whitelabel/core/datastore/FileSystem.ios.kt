package com.whitelabel.core.datastore

import kotlinx.cinterop.ExperimentalForeignApi
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

internal actual val SystemFileSystem: FileSystem = FileSystem.SYSTEM

/**
 * `<Application Support>/datastore/<name>.json`, the conventional location for a [JsonStore] file
 * on iOS (kept by backups, hidden from the user). Creates the directory.
 */
@OptIn(ExperimentalForeignApi::class)
fun iosJsonStorePath(name: String): Path {
    val support = NSFileManager.defaultManager.URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null)
    val dir = checkNotNull(support?.path) { "No Application Support directory" }.toPath() / "datastore"
    FileSystem.SYSTEM.createDirectories(dir)
    return dir / "$name.json"
}
