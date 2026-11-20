package com.whitelabel.core.datastore

import kotlinx.cinterop.ExperimentalForeignApi
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import platform.posix.F_FULLFSYNC
import platform.posix.O_RDWR
import platform.posix.close
import platform.posix.fcntl
import platform.posix.fsync
import platform.posix.open

internal actual val SystemFileSystem: FileSystem = FileSystem.SYSTEM

/**
 * okio's file handle `flush()` is only `fflush()` on iOS, so the bytes may still sit in the OS cache.
 * `F_FULLFSYNC` asks the drive to write them; `fsync()` is the fallback where that's unsupported.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun syncToDisk(fileSystem: FileSystem, path: Path) {
    if (fileSystem !== FileSystem.SYSTEM) return
    val fd = open(path.toString(), O_RDWR)
    if (fd == -1) throw IOException("Couldn't open the copy to sync it")
    try {
        if (fcntl(fd, F_FULLFSYNC) == -1 && fsync(fd) == -1) throw IOException("Couldn't sync the copy")
    } finally {
        close(fd)
    }
}

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
