package com.whitelabel.core.data.session

import com.whitelabel.core.common.DataError
import com.whitelabel.core.common.EmptyResult
import com.whitelabel.core.common.Result
import com.whitelabel.core.common.asEmptyResult
import com.whitelabel.core.data.di.ApplicationScope
import com.whitelabel.core.datastore.StoredTokens
import com.whitelabel.core.domain.Registration
import com.whitelabel.core.domain.SessionRepository
import com.whitelabel.core.domain.SessionState
import com.whitelabel.core.network.postPublicResult
import com.whitelabel.core.network.publicRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class DefaultSessionRepository @Inject constructor(
    private val client: HttpClient,
    private val session: LocalSession,
    @ApplicationScope private val scope: CoroutineScope,
) : SessionRepository {

    override val state: StateFlow<SessionState> = session.tokens
        .map { if (it == null) SessionState.SignedOut else SessionState.SignedIn }
        .stateIn(scope, SharingStarted.Eagerly, SessionState.Unknown)

    override suspend fun signIn(email: String, password: String): EmptyResult<DataError> =
        start(client.postPublicResult<LoginRequest, AuthResultDto>("auth/login", LoginRequest(email.trim(), password)))

    override suspend fun register(registration: Registration): EmptyResult<DataError> {
        val body = RegisterRequest(
            email = registration.email.trim(),
            password = registration.password,
            firstName = registration.firstName?.trim()?.ifEmpty { null },
            lastName = registration.lastName?.trim()?.ifEmpty { null },
        )
        return start(client.postPublicResult<RegisterRequest, AuthResultDto>("auth/register", body))
    }

    override suspend fun signOut() {
        val refreshToken = session.currentTokens()?.refreshToken
        session.clear()
        if (refreshToken == null) return
        // Outlives the screen that asked: the screen goes away as soon as the state flips.
        scope.launch {
            try {
                client.post("auth/logout") {
                    publicRequest()
                    contentType(ContentType.Application.Json)
                    setBody(LogoutRequest(refreshToken))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Best effort: the session is already gone locally and expires on the server.
            }
        }
    }

    private suspend fun start(result: Result<AuthResultDto, DataError>): EmptyResult<DataError> {
        if (result is Result.Success) {
            val tokens = result.data.tokens
            session.start(result.data.user, StoredTokens(tokens.accessToken, tokens.refreshToken))
        }
        return result.asEmptyResult()
    }
}
