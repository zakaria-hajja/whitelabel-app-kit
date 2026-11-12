package com.whitelabel.core.ads

import com.whitelabel.core.datastore.JsonStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import okio.FileSystem
import okio.Path

@Serializable
internal data class StoredLaunches(val count: Int = 0)

/**
 * Counts app launches in a JSON store at [path] (`ads-launches.json`). [launchNumber] is 0 until
 * [countThisLaunch] has run.
 */
class LaunchCounter(path: Path, scope: CoroutineScope, fileSystem: FileSystem = FileSystem.SYSTEM) {
    private val store = JsonStore(path, StoredLaunches.serializer(), StoredLaunches(), scope, fileSystem)
    private val lock = Mutex()
    private val number = MutableStateFlow(0)

    val launchNumber: StateFlow<Int> = number.asStateFlow()

    /** Increments the persisted count once per process; later calls return the same number. */
    suspend fun countThisLaunch(): Int = lock.withLock {
        if (number.value == 0) {
            store.update { it.copy(count = it.count + 1) }
            number.value = store.data.first().count
        }
        number.value
    }
}
