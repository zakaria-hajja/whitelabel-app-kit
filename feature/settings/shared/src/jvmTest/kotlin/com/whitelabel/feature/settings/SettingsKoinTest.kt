package com.whitelabel.feature.settings

import org.junit.jupiter.api.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

@OptIn(KoinExperimentalAPI::class)
class SettingsKoinTest {

    @Test fun `settings module resolves with the core types`() {
        settingsModule.verify(
            extraTypes = listOf(
                com.whitelabel.core.domain.SessionRepository::class,
                com.whitelabel.core.domain.UserPreferencesRepository::class,
                com.whitelabel.core.domain.LanguageRepository::class,
                com.whitelabel.core.domain.AppConfigRepository::class,
                com.whitelabel.core.branding.BrandConfig::class,
                com.whitelabel.core.analytics.Analytics::class,
                io.ktor.client.HttpClient::class,
            ),
        )
    }
}
