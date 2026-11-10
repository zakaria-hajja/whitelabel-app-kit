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

    /**
     * Stores the pair a refresh returned, only if the session still holds [expected]: a refresh that
     * finishes after sign-out, or after another sign-in, must not write its pair. Returns whether it did.
     */
    suspend fun replace(expected: SessionTokens, new: SessionTokens): Boolean

    /**
     * The backend ended the session that used [expected] (refresh rejected, or the retry still
     * unauthorized): clear local session data, unless a newer session has started since.
     */
    suspend fun endSession(expected: SessionTokens)
}
