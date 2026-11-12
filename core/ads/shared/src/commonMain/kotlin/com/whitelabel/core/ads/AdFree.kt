package com.whitelabel.core.ads

import com.whitelabel.core.datastore.JsonStore
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import okio.FileSystem
import okio.Path

sealed interface AdFreeState {
    data object Inactive : AdFreeState
    data class Active(val until: Instant) : AdFreeState
}

@Serializable
internal data class StoredAdFree(val untilEpochMillis: Long? = null)

/** An ad-free window persisted in a JSON store at [path] (`ads-free.json`). */
class AdFree(path: Path, private val clock: Clock, scope: CoroutineScope, fileSystem: FileSystem = FileSystem.SYSTEM) {
    private val store = JsonStore(path, StoredAdFree.serializer(), StoredAdFree(), scope, fileSystem)
    private val until = MutableStateFlow<Instant?>(null)

    private val isLoaded = MutableStateFlow(false)

    /** False until the stored window has been read once; treat "not loaded" as ads hidden. */
    val loaded: StateFlow<Boolean> = isLoaded.asStateFlow()

    init {
        scope.launch {
            store.data.collect { doc ->
                // Windows only grow, so never let a stale stored value undo a grant made in memory.
                publish(doc.untilEpochMillis?.let(Instant::fromEpochMilliseconds))
                isLoaded.value = true
            }
        }
    }

    private fun publish(end: Instant?) {
        if (end != null) until.update { cur -> if (cur == null || end > cur) end else cur }
    }

    /** Re-evaluated against the clock on every read, and on collect also when the window ends. */
    val state: StateFlow<AdFreeState> = ClockedState()

    /** Sets the window end to [until], but never shortens an active window. */
    suspend fun grantUntil(until: Instant) {
        var written = until
        store.update { cur ->
            val keep = cur.untilEpochMillis?.let(Instant::fromEpochMilliseconds)
            written = maxOf(until, keep ?: until)
            cur.copy(untilEpochMillis = written.toEpochMilliseconds())
        }
        publish(written)
        isLoaded.value = true
    }

    /** Adds [by] to the later of now and the current end. */
    suspend fun extend(by: Duration) {
        var written = clock.now()
        store.update { cur ->
            val current = cur.untilEpochMillis?.let(Instant::fromEpochMilliseconds)
            val now = clock.now()
            written = maxOf(now, current ?: now) + by
            cur.copy(untilEpochMillis = written.toEpochMilliseconds())
        }
        publish(written)
        isLoaded.value = true
    }

    private fun evaluate(end: Instant?): AdFreeState =
        if (end != null && end > clock.now()) AdFreeState.Active(end) else AdFreeState.Inactive

    @OptIn(ExperimentalCoroutinesApi::class)
    private inner class ClockedState : StateFlow<AdFreeState> {
        private val changes: Flow<AdFreeState> = until.transformLatest { end ->
            emit(evaluate(end))
            if (end != null) {
                val remaining = end - clock.now()
                if (remaining.isPositive()) {
                    delay(remaining)
                    emit(evaluate(end))
                }
            }
        }.distinctUntilChanged()

        override val value: AdFreeState get() = evaluate(until.value)
        override val replayCache: List<AdFreeState> get() = listOf(value)
        override suspend fun collect(collector: FlowCollector<AdFreeState>): Nothing {
            changes.collect(collector)
            awaitCancellation()
        }
    }
}
