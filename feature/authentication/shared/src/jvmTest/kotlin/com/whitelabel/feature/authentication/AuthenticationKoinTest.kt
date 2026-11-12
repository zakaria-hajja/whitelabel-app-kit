package com.whitelabel.feature.authentication

import androidx.lifecycle.SavedStateHandle
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.whitelabel.core.analytics.Analytics
import com.whitelabel.core.domain.DebugGuestAccount
import com.whitelabel.core.domain.SessionRepository
import com.whitelabel.feature.authentication.presentation.login.LoginViewModel
import com.whitelabel.feature.authentication.presentation.FakeAnalytics
import com.whitelabel.feature.authentication.presentation.FakeSession
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import org.junit.jupiter.api.Test
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
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

    @Test fun `the login view model gets the debug guest account only when one is bound`() {
        fun loginWith(guest: DebugGuestAccount?): LoginViewModel {
            val core = module {
                single<SessionRepository> { FakeSession() }
                single<Analytics> { FakeAnalytics() }
                single { HttpClient(MockEngine { respondOk() }) }
                guest?.let { account -> single { account } }
            }
            return koinApplication { modules(core, authenticationModule) }.koin.get { parametersOf(SavedStateHandle()) }
        }
        assertThat(loginWith(DebugGuestAccount("a@b.test", "pw")).state.value.guestAvailable).isTrue()
        assertThat(loginWith(null).state.value.guestAvailable).isFalse()
    }
}
