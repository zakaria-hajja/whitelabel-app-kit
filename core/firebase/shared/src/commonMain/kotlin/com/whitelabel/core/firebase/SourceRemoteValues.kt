package com.whitelabel.core.firebase

import com.whitelabel.core.domain.remote.RemoteValues
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/** Typed, never-throwing reads over a [RemoteConfigSource]. Missing, empty or malformed values give the default. */
class SourceRemoteValues(
    private val source: RemoteConfigSource,
    private val scope: CoroutineScope,
) : RemoteValues {
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val changes: Flow<Unit> = _changes.asSharedFlow()

    private val json = Json { ignoreUnknownKeys = false }

    /** One fetch-and-activate in the background; [changes] emits when it activated new values. */
    fun start() {
        scope.launch {
            // A throwing platform source counts as "nothing changed".
            runCatching { source.fetchAndActivate { changed -> if (changed) _changes.tryEmit(Unit) } }
        }
    }

    private fun value(key: String): String? = runCatching { source.raw(key) }.getOrNull()?.takeIf { it.isNotEmpty() }

    override fun long(key: String, default: Long): Long = value(key)?.trim()?.toLongOrNull() ?: default

    override fun double(key: String, default: Double): Double =
        value(key)?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: default

    override fun string(key: String, default: String): String = value(key) ?: default

    override fun boolean(key: String, default: Boolean): Boolean =
        when (value(key)?.trim()?.lowercase()) {
            "true", "1" -> true
            "false", "0" -> false
            else -> default
        }

    override fun <T> json(key: String, default: T, serializer: KSerializer<T>): T {
        val raw = value(key) ?: return default
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(default)
    }
}
