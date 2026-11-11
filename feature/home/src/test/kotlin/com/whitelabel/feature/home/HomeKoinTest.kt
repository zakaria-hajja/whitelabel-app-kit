package com.whitelabel.feature.home

import org.junit.jupiter.api.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

@OptIn(KoinExperimentalAPI::class)
class HomeKoinTest {

    @Test fun `home module resolves with the core types`() {
        homeModule.verify(
            extraTypes = listOf(
                com.whitelabel.core.domain.UserRepository::class,
            ),
        )
    }
}
