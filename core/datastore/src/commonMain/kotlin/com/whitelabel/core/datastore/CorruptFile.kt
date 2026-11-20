package com.whitelabel.core.datastore

import okio.ByteString
import okio.FileNotFoundException
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.buffer
import okio.use

/** What a [JsonStore] does with a file it can't decode. */
enum class CorruptFilePolicy {
    /** Keep the file's bytes as `<name>.corrupt` next to it, then replace the file with the default. */
    KEEP_COPY_THEN_DEFAULT,

    /** Leave the file alone and fail every read and update with [CorruptStoreException]. */
    FAIL,
}

/** What happened to a file a [JsonStore] couldn't decode. Names no file, path or content, by design. */
enum class CorruptFileEvent {
    /** The bytes were kept as `<name>.corrupt`; the store now holds its default. */
    COPY_KEPT,

    /** The copy couldn't be written; nothing was replaced and the read failed. */
    COPY_FAILED,

    /** [CorruptFilePolicy.FAIL]: the file was left as it is and the read failed. */
    LEFT_IN_PLACE,
}

/**
 * A [JsonStore] read or update that couldn't be served because the file doesn't decode. It has a
 * fixed message and never a cause, so it carries no file content, name or path.
 */
class CorruptStoreException internal constructor() : IOException("JSON store unreadable")

/** Where the copy of a corrupt [this] is kept: `<name>.corrupt` in the same folder. */
internal fun Path.corruptCopy(): Path = requireNotNull(parent) { "A JSON store path needs a folder" } / "$name.corrupt"

/** What [keepCorruptCopy] did. */
internal enum class CopyResult {
    /** The bytes are on disk as `<name>.corrupt`. */
    KEPT,

    /** The copy couldn't be written (or the file couldn't be read again). */
    FAILED,

    /** The file is missing or decodes by now: there is nothing to keep. */
    NOTHING_TO_KEEP,
}

/**
 * Re-reads [path]. If its bytes still fail [decodes], writes those exact bytes to
 * `<name>.corrupt.tmp`, makes them durable, and atomically moves that file onto [Path.corruptCopy],
 * replacing any older copy. It copies only the bytes it checked, so a file that decodes is never
 * copied. On an I/O error the temp file is removed best-effort and the result is [CopyResult.FAILED].
 */
internal fun keepCorruptCopy(fileSystem: FileSystem, path: Path, decodes: (ByteString) -> Boolean): CopyResult {
    val bytes = try {
        fileSystem.read(path) { readByteString() }
    } catch (_: FileNotFoundException) {
        return CopyResult.NOTHING_TO_KEEP
    } catch (_: IOException) {
        return CopyResult.FAILED
    }
    if (decodes(bytes)) return CopyResult.NOTHING_TO_KEEP
    val copy = path.corruptCopy()
    val tmp = copy.parent!! / "${copy.name}.tmp"
    return try {
        // Durability: DataStore fsyncs the default it then writes over the original, so the copy must
        // reach the disk first, or a power loss can lose both. Don't "simplify" this to
        // fileSystem.write {}: a plain sink() never fsyncs. A file handle's flush() does on the JVM and
        // Android (FileDescriptor.sync()); on iOS okio only fflush()es, so syncToDisk adds the fsync.
        fileSystem.delete(tmp, mustExist = false)
        fileSystem.openReadWrite(tmp, mustCreate = true).use { handle ->
            handle.sink().buffer().use { it.write(bytes) }
            handle.flush()
        }
        syncToDisk(fileSystem, tmp)
        fileSystem.atomicMove(tmp, copy)
        CopyResult.KEPT
    } catch (_: IOException) {
        try {
            fileSystem.delete(tmp, mustExist = false)
        } catch (_: IOException) {
            // Best effort: the next attempt deletes a stale .tmp before it writes.
        }
        CopyResult.FAILED
    }
}

/**
 * Makes [path]'s bytes durable where a file handle's `flush()` doesn't (iOS). A no-op on the JVM and
 * Android, and for any file system other than the platform's real one. Throws [IOException] with a
 * fixed message (never the path) when the sync fails.
 */
internal expect fun syncToDisk(fileSystem: FileSystem, path: Path)
