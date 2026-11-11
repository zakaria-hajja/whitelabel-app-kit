package com.whitelabel.core.data.config

import com.whitelabel.core.datastore.JsonStore
import okio.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

@Serializable
private data class CachedConfig(val config: AppConfigDto? = null)

/** The last fetched remote config. Kept across sign-out: it belongs to the app, not the user. */
internal class AppConfigCache(path: Path, scope: CoroutineScope) {
    private val store = JsonStore(path, CachedConfig.serializer(), CachedConfig(), scope)

    val config: Flow<AppConfigDto?> = store.data.map { it.config }

    suspend fun write(config: AppConfigDto) = store.update { CachedConfig(config) }
}
