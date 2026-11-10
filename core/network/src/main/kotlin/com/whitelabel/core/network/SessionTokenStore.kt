package com.whitelabel.core.network

/** The signed-in session's token pair. */
data class SessionTokens(val accessToken: String, val refreshToken: String)

/**
 * Where the HTTP client reads and updates the session tokens. `:core:data` implements it on the
 * encrypted token store; the network layer knows nothing about storage.
 */
interface SessionTokenStore {
    /** The current pair, or `null` when signed out. */
    suspend fun current(): SessionTokens?

    /** Stores the pair a refresh returned. */
    suspend fun replace(tokens: SessionTokens)

    /** The backend ended the session (refresh rejected, or the session is gone): clear local session data. */
    suspend fun endSession()
}
