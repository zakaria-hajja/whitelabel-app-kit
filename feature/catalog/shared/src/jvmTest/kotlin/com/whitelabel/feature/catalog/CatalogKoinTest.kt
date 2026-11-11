package com.whitelabel.feature.catalog

import org.junit.jupiter.api.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

@OptIn(KoinExperimentalAPI::class)
class CatalogKoinTest {

    @Test fun `catalog module resolves with the core types`() {
        catalogModule.verify(
            extraTypes = listOf(
                com.whitelabel.feature.catalog.CatalogOptions::class,
                com.whitelabel.core.analytics.Analytics::class,
                androidx.lifecycle.SavedStateHandle::class,
                io.ktor.client.HttpClient::class,
            ),
        )
    }
}
