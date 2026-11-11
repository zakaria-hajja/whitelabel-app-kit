package com.whitelabel.core.domain

import com.whitelabel.core.common.DataError
import com.whitelabel.core.common.EmptyResult
import kotlinx.coroutines.flow.StateFlow

/** Whether a session exists. [Unknown] lasts only until the stored tokens have been read. */
sealed interface SessionState {
    data object Unknown : SessionState
    data object SignedOut : SessionState
    data object SignedIn : SessionState
}

/** A new account. Blank names are sent as absent. */
data class Registration(
    val email: String,
    val password: String,
    val firstName: String?,
    val lastName: String?,
)

/** Signing in and out. A successful sign-in or registration flips [state] to [SessionState.SignedIn]. */
interface SessionRepository {
    val state: StateFlow<SessionState>

    suspend fun signIn(email: String, password: String): EmptyResult<DataError>

    suspend fun register(registration: Registration): EmptyResult<DataError>

    /** Signs out locally at once (works offline) and tells the backend in the background. */
    suspend fun signOut()
}
