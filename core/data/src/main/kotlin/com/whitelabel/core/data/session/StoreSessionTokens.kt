package com.whitelabel.core.data.session

import com.whitelabel.core.datastore.StoredTokens
import com.whitelabel.core.network.SessionTokenStore
import com.whitelabel.core.network.SessionTokens
import javax.inject.Inject

/** The HTTP client's view of the stored session. */
internal class StoreSessionTokens @Inject constructor(
    private val session: LocalSession,
) : SessionTokenStore {

    override suspend fun current(): SessionTokens? =
        session.currentTokens()?.let { SessionTokens(it.accessToken, it.refreshToken) }

    override suspend fun replace(expected: SessionTokens, new: SessionTokens): Boolean =
        session.replaceTokens(expected.stored(), new.stored())

    override suspend fun endSession(expected: SessionTokens) = session.endIfCurrent(expected.stored())

    private fun SessionTokens.stored() = StoredTokens(accessToken, refreshToken)
}
