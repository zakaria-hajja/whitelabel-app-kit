package com.whitelabel.feature.authentication

import org.junit.jupiter.api.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

@OptIn(KoinExperimentalAPI::class)
class AuthenticationKoinTest {

    @Test fun `authentication module resolves with the core types`() {
        authenticationModule.verify(
            extraTypes = listOf(
                com.whitelabel.core.domain.SessionRepository::class,
                com.whitelabel.core.analytics.Analytics::class,
                androidx.lifecycle.SavedStateHandle::class,
                io.ktor.client.HttpClient::class,
            ),
        )
    }
}
