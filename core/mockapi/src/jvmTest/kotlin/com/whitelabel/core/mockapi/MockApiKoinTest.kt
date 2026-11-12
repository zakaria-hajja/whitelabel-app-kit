package com.whitelabel.core.mockapi

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.whitelabel.core.domain.DebugGuestAccount
import com.whitelabel.core.network.EngineOverride
import org.junit.jupiter.api.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.koinApplication
import org.koin.test.verify.verify

@OptIn(KoinExperimentalAPI::class)
class MockApiKoinTest {

    @Test fun `mock api module resolves on its own`() {
        mockApiModule.verify()
    }

    @Test fun `the engine override serves the one fake backend`() {
        val koin = koinApplication { modules(mockApiModule) }.koin
        assertThat(koin.get<EngineOverride>().engine()).isSameInstanceAs(koin.get<FakeBackend>().engine)
    }

    @Test fun `the contributor hands the shell the mock api module`() {
        assertThat(MockApiContributor().modules()).containsExactly(mockApiModule)
    }

    @Test fun `the fake backend offers its seeded guest account`() {
        val koin = koinApplication { modules(mockApiModule) }.koin
        val guest = koin.get<DebugGuestAccount>()
        assertThat(guest).isEqualTo(DebugGuestAccount(FakeData.GUEST_EMAIL, FakeData.PASSWORD))
        assertThat(FakeData.users().any { it.email == guest.email && it.password == guest.password }).isTrue()
    }
}
