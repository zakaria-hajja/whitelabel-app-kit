package com.whitelabel.core.data.user

import com.whitelabel.core.datastore.JsonStore
import com.whitelabel.core.domain.User
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

@Serializable
private data class CachedUser(val user: UserDto? = null)

/** The last known signed-in user, so screens show it offline. Cleared on sign-out. */
internal class UserCache(file: File, scope: CoroutineScope) {
    private val store = JsonStore(file, CachedUser.serializer(), CachedUser(), scope)

    val user: Flow<User?> = store.data.map { it.user?.toUser() }

    suspend fun write(user: UserDto) = store.update { CachedUser(user) }

    suspend fun clear() = store.update { CachedUser() }
}
