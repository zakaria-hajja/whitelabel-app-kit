package com.whitelabel.core.datastore

import okio.ByteString
import okio.FileNotFoundException
import okio.FileSystem
import okio.IOException
import okio.Path

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

/**
 * Re-reads [path]. If its bytes still fail [decodes], writes those exact bytes to
 * `<name>.corrupt.tmp` and atomically moves that file onto [Path.corruptCopy], replacing any older
 * copy. Returns [CorruptFileEvent.COPY_KEPT]; [CorruptFileEvent.COPY_FAILED] on an I/O error (the
 * temp file is removed best-effort); or `null` when the file is missing or decodes by now, so there
 * is nothing to keep. It copies only the bytes it checked, so a file that decodes is never copied.
 */
internal fun keepCorruptCopy(fileSystem: FileSystem, path: Path, decodes: (ByteString) -> Boolean): CorruptFileEvent? {
    val bytes = try {
        fileSystem.read(path) { readByteString() }
    } catch (_: FileNotFoundException) {
        return null
    } catch (_: IOException) {
        return CorruptFileEvent.COPY_FAILED
    }
    if (decodes(bytes)) return null
    val copy = path.corruptCopy()
    val tmp = copy.parent!! / "${copy.name}.tmp"
    return try {
        fileSystem.write(tmp) { write(bytes) }
        fileSystem.atomicMove(tmp, copy)
        CorruptFileEvent.COPY_KEPT
    } catch (_: IOException) {
        try {
            fileSystem.delete(tmp, mustExist = false)
        } catch (_: IOException) {
            // Best effort: a stale .tmp is overwritten by the next attempt.
        }
        CorruptFileEvent.COPY_FAILED
    }
}
