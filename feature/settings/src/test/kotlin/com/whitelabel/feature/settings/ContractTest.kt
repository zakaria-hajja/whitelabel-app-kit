package com.whitelabel.feature.settings

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.whitelabel.core.common.Result
import com.whitelabel.core.testing.ContractBackend
import com.whitelabel.core.testing.OpenApiSpec
import com.whitelabel.core.testing.exampleValue
import com.whitelabel.feature.settings.data.KtorSettingsDataSource
import com.whitelabel.feature.settings.domain.NotificationSettings
import com.whitelabel.feature.settings.domain.SettingsPatch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ContractTest {
    private val backend = ContractBackend()
    private val source = KtorSettingsDataSource(backend.client())

    @Test fun `get api v1 settings matches the contract`() = runTest {
        val result = source.get()
        assertThat(result).isInstanceOf(Result.Success::class)
        val push = OpenApiSpec.exampleValue("/api/v1/settings", "get", 200, "data.notifications.push").toBoolean()
        assertThat(((result as Result.Success<NotificationSettings>).data).push).isEqualTo(push)
    }

    @Test fun `patch api v1 settings matches the contract`() = runTest {
        assertThat(source.patch(SettingsPatch(theme = "DARK", language = "fr", push = false))).isEqualTo(Result.Success(Unit))
        backend.assertRequestMatches("patch", "/api/v1/settings")
    }

    @Test fun `the smallest valid settings response still decodes`() = runTest {
        assertThat(KtorSettingsDataSource(ContractBackend(minimal = true).client()).get()).isInstanceOf(Result.Success::class)
    }
}
