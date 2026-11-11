package com.whitelabel.core.domain

import com.whitelabel.core.common.DataError
import com.whitelabel.core.common.EmptyResult
import kotlinx.coroutines.flow.Flow

/** The signed-in user. Names are optional on the backend. */
data class User(
    val id: String,
    val email: String,
    val firstName: String?,
    val lastName: String?,
)

/** The signed-in user, cached so screens show it offline. Shared by home, profile and settings. */
interface UserRepository {
    /** The cached user; `null` when signed out. */
    val user: Flow<User?>

    /** Fetches the user from the backend and updates the cache. */
    suspend fun refresh(): EmptyResult<DataError>

    /** Saves the names (`null` clears one) and caches the user the backend returns. */
    suspend fun update(firstName: String?, lastName: String?): EmptyResult<DataError>
}
