package com.whitelabel.core.data.session

import com.whitelabel.core.data.user.UserCache
import com.whitelabel.core.data.user.UserDto
import com.whitelabel.core.datastore.StoredTokens
import com.whitelabel.core.datastore.TokenStore
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** Everything stored for the signed-in user, started and cleared together. */
internal class LocalSession @Inject constructor(
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

    suspend fun replaceTokens(tokens: StoredTokens) = tokenStore.write(tokens)

    /** Tokens first, so the session ends even if clearing the cache fails. */
    suspend fun clear() {
        tokenStore.clear()
        userCache.clear()
    }
}
