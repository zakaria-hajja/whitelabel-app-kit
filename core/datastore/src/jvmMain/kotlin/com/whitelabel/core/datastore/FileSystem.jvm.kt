package com.whitelabel.core.datastore

import okio.FileSystem
import okio.Path

internal actual val SystemFileSystem: FileSystem = FileSystem.SYSTEM

/** Nothing to do: okio's file handle `flush()` already fsyncs here (`FileDescriptor.sync()`). */
internal actual fun syncToDisk(fileSystem: FileSystem, path: Path) = Unit
