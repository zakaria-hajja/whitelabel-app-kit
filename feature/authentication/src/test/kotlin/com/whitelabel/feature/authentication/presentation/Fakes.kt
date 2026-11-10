package com.whitelabel.feature.authentication.presentation

import com.whitelabel.core.analytics.Analytics
import com.whitelabel.core.analytics.AnalyticsEvent
import com.whitelabel.core.common.DataError
import com.whitelabel.core.common.EmptyResult
import com.whitelabel.core.common.Result
import com.whitelabel.core.domain.Registration
import com.whitelabel.core.domain.SessionRepository
import com.whitelabel.core.domain.SessionState
import com.whitelabel.feature.authentication.domain.PasswordResetDataSource
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSession : SessionRepository {
    override val state = MutableStateFlow<SessionState>(SessionState.SignedOut)
    var signIns = 0
    var registrations = mutableListOf<Registration>()
    /** Suspends until it returns: lets a test hold a call in flight. */
    var answer: suspend () -> EmptyResult<DataError> = { Result.Success(Unit) }

    override suspend fun signIn(email: String, password: String): EmptyResult<DataError> {
        signIns++
        return answer()
    }

    override suspend fun register(registration: Registration): EmptyResult<DataError> {
        registrations += registration
        return answer()
    }

    override suspend fun signOut() = Unit
}

class FakeAnalytics : Analytics {
    val events = mutableListOf<AnalyticsEvent>()
    override fun track(event: AnalyticsEvent) {
        events += event
    }
}

class FakePasswordReset : PasswordResetDataSource {
    var requests = mutableListOf<String>()
    var resets = 0
    var answer: suspend () -> EmptyResult<DataError> = { Result.Success(Unit) }

    override suspend fun requestReset(email: String): EmptyResult<DataError> {
        requests += email
        return answer()
    }

    override suspend fun reset(token: String, newPassword: String): EmptyResult<DataError> {
        resets++
        return answer()
    }
}
