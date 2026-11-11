package com.whitelabel.core.data.di

import com.whitelabel.core.branding.BrandConfig
import com.whitelabel.core.branding.Environment
import com.whitelabel.core.branding.RuntimeDefaults
import com.whitelabel.core.datastore.Sealed
import com.whitelabel.core.datastore.StorePaths
import com.whitelabel.core.datastore.TokenCipher
import com.whitelabel.core.domain.AppConfigRepository
import com.whitelabel.core.domain.LanguageRepository
import com.whitelabel.core.domain.SessionRepository
import com.whitelabel.core.domain.UserPreferencesRepository
import com.whitelabel.core.domain.UserRepository
import com.whitelabel.core.network.NetworkConfig
import com.whitelabel.core.network.di.networkModule
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID

/** The data graph builds on iOS from the pieces the iOS app will provide (here as fakes). */
class DataKoinIosTest {

    private val dir = (NSTemporaryDirectory() + "data-koin-" + NSUUID().UUIDString).toPath().also { FileSystem.SYSTEM.createDirectories(it) }
    private val app = koinApplication {
        modules(
            dataModule,
            networkModule,
            module {
                single { StorePaths { name -> dir / "$name.json" } }
                single<TokenCipher> {
                    object : TokenCipher {
                        override fun encrypt(plain: ByteArray) = Sealed(ByteArray(12), plain)
                        override fun decrypt(sealed: Sealed) = sealed.ciphertext
                    }
                }
                single<LanguageRepository> {
                    object : LanguageRepository {
                        override fun current(): String? = null
                        override fun set(languageTag: String?) = Unit
                    }
                }
                single<HttpClientEngine> { MockEngine { respondError(HttpStatusCode.ServiceUnavailable) } }
                single { NetworkConfig(baseUrl = "https://example.invalid/api/v1/", tenant = null, userAgent = "ios-test") }
                single {
                    BrandConfig(
                        tenant = null, appName = "Test", applicationId = "com.example.test", versionName = "1.0",
                        environment = Environment.DEVELOPMENT, apiBaseUrl = null, deepLinkScheme = "test",
                        runtimeDefaults = RuntimeDefaults(supportEmail = null, privacyPolicyUrl = "", termsUrl = "", enabledFeatures = emptySet()),
                    )
                }
            },
        )
    }

    @AfterTest fun close() {
        app.close()
        FileSystem.SYSTEM.deleteRecursively(dir)
    }

    @Test fun `the data graph resolves with platform fakes`() {
        val koin = app.koin
        assertNotNull(koin.get<SessionRepository>())
        assertNotNull(koin.get<UserRepository>())
        assertNotNull(koin.get<AppConfigRepository>())
        assertNotNull(koin.get<UserPreferencesRepository>())
    }
}
