package com.whitelabel.feature.profile

import org.junit.jupiter.api.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

@OptIn(KoinExperimentalAPI::class)
class ProfileKoinTest {

    @Test fun `profile module resolves with the core types`() {
        profileModule.verify(
            extraTypes = listOf(
                com.whitelabel.core.domain.UserRepository::class,
                io.ktor.client.HttpClient::class,
            ),
        )
    }
}
