package com.whitelabel.core.data.session

import com.whitelabel.core.data.user.UserCache
import com.whitelabel.core.data.user.UserDto
import com.whitelabel.core.datastore.StoredTokens
import com.whitelabel.core.datastore.TokenStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** Everything stored for the signed-in user, started and cleared together. */
internal class LocalSession(
    private val tokenStore: TokenStore,
    private val userCache: UserCache,
) {
    val tokens: Flow<StoredTokens?> = tokenStore.tokens

    suspend fun currentTokens(): StoredTokens? = tokenStore.tokens.first()

    /** Writes the user before the tokens, so a signed-in state never shows without its user. */
    suspend fun start(user: UserDto, tokens: StoredTokens) {
        userCache.write(user)
        tokenStore.write(tokens)
    }

    /** Replaces the pair only if it still is [expected]; `false` means the session changed meanwhile. */
    suspend fun replaceTokens(expected: StoredTokens, new: StoredTokens): Boolean = tokenStore.compareAndSet(expected, new)

    /** Ends the session only if it still holds [expected], so a stale failure never ends a newer one. */
    suspend fun endIfCurrent(expected: StoredTokens) {
        if (tokenStore.compareAndSet(expected, null)) userCache.clear()
    }

    /** Tokens first, so the session ends even if clearing the cache fails. */
    suspend fun clear() {
        tokenStore.clear()
        userCache.clear()
    }
}
