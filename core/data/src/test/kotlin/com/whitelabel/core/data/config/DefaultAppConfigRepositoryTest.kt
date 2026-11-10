package com.whitelabel.core.data.config

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.whitelabel.core.branding.BrandConfig
import com.whitelabel.core.branding.Environment
import com.whitelabel.core.branding.RuntimeDefaults
import com.whitelabel.core.common.Result
import com.whitelabel.core.data.mockClient
import com.whitelabel.core.data.ok
import com.whitelabel.core.domain.AppConfig
import com.whitelabel.core.domain.ThemePreference
import io.ktor.client.HttpClient
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

private val brand = BrandConfig(
    tenant = null,
    appName = "Sample",
    applicationId = "com.example.sample",
    versionName = "1.0.0",
    environment = Environment.DEVELOPMENT,
    apiBaseUrl = "https://api.test/api/v1/",
    deepLinkScheme = "sample",
    runtimeDefaults = RuntimeDefaults(
        supportEmail = "help@sample.example",
        privacyPolicyUrl = "https://sample.example/privacy",
        termsUrl = "https://sample.example/terms",
        enabledFeatures = setOf("catalog"),
    ),
)

private val defaults = AppConfig(
    features = emptyMap(),
    supportEmail = "help@sample.example",
    privacyPolicyUrl = "https://sample.example/privacy",
    termsUrl = "https://sample.example/terms",
    defaultTheme = ThemePreference.SYSTEM,
    supportedLanguages = emptyList(),
)

private const val REMOTE = """{
  "tenant":{"slug":"sample","name":"Sample"},
  "app":{"displayName":"Sample"},
  "features":{"catalog":false,"profile":true},
  "support":{},
  "legal":{"privacyPolicyUrl":"https://remote.example/privacy","termsUrl":"https://remote.example/terms"},
  "runtime":{"defaultLanguage":"en","supportedLanguages":["en","fr"],"defaultTheme":"DARK"}
}"""

class DefaultAppConfigRepositoryTest {

    @TempDir
    lateinit var dir: File

    private suspend fun <R> withRepository(client: HttpClient, block: suspend (DefaultAppConfigRepository) -> R): R {
        val job = Job()
        val scope = CoroutineScope(Dispatchers.IO + job)
        return try {
            block(DefaultAppConfigRepository(client, brand, AppConfigCache(File(dir, "app_config.json"), scope), scope))
        } finally {
            job.cancelAndJoin()
        }
    }

    private fun offline() = mockClient { throw IOException("offline") }

    @Test fun `defaults before any fetch come from brand config`() = runTest {
        withRepository(offline()) { repository ->
            assertThat(repository.config.value).isEqualTo(defaults)
            assertThat(repository.config.first()).isEqualTo(defaults)
        }
    }

    @Test fun `fetched config is cached across instances`() = runTest {
        val expected = AppConfig(
            features = mapOf("catalog" to false, "profile" to true),
            supportEmail = "help@sample.example",
            privacyPolicyUrl = "https://remote.example/privacy",
            termsUrl = "https://remote.example/terms",
            defaultTheme = ThemePreference.DARK,
            supportedLanguages = listOf("en", "fr"),
        )
        withRepository(mockClient { ok(REMOTE) }) { repository ->
            assertThat(repository.refresh()).isEqualTo(Result.Success(Unit))
            assertThat(repository.config.first { it != defaults }).isEqualTo(expected)
        }
        withRepository(offline()) { repository ->
            assertThat(repository.config.first { it != defaults }).isEqualTo(expected)
        }
    }

    @Test fun `failed refresh keeps the cached config`() = runTest {
        withRepository(mockClient { ok(REMOTE) }) { it.refresh() }
        withRepository(offline()) { repository ->
            assertThat(repository.refresh()).isInstanceOf(Result.Error::class)
            assertThat(repository.config.first { it != defaults }.defaultTheme).isEqualTo(ThemePreference.DARK)
        }
    }

    @Test fun `unknown theme and unknown flag keys still load`() = runTest {
        val newer = REMOTE.replace("\"DARK\"", "\"SEPIA\"").replace("\"profile\":true", "\"profile\":true,\"x_new\":true")
        withRepository(mockClient { ok(newer) }) { repository ->
            assertThat(repository.refresh()).isEqualTo(Result.Success(Unit))
            val config = repository.config.first { it != defaults }
            assertThat(config.defaultTheme).isEqualTo(ThemePreference.SYSTEM)
            assertThat(config.features["x_new"]).isEqualTo(true)
        }
    }
}
