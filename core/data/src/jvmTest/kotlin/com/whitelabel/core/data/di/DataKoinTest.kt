package com.whitelabel.core.data.di

import assertk.assertThat
import assertk.assertions.isFalse
import com.whitelabel.core.branding.BrandConfig
import com.whitelabel.core.datastore.Sealed
import com.whitelabel.core.datastore.StorePaths
import com.whitelabel.core.datastore.TokenCipher
import com.whitelabel.core.domain.LanguageRepository
import io.ktor.client.HttpClient
import java.io.File
import okio.Path.Companion.toOkioPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.verify.verify

@OptIn(KoinExperimentalAPI::class)
class DataKoinTest {

    @Test fun `data module resolves with the network client, the brand and the platform's pieces`() {
        // Path: each store's path comes from StorePaths inside its definition, which verify() can't see.
        // androidDataModule is checked on a device by the apps' graph tests (it needs a Context).
        dataModule.verify(
            extraTypes = listOf(
                HttpClient::class, BrandConfig::class, StorePaths::class, TokenCipher::class, LanguageRepository::class,
                okio.Path::class, okio.FileSystem::class,
            ),
        )
    }

    @Test fun `closing the graph cancels the application scope`(@TempDir dir: File) {
        val platform = module {
            single { StorePaths { name -> File(dir, "$name.json").toOkioPath() } }
            single<TokenCipher> { PlainCipher }
            single<LanguageRepository> { NoLanguage }
        }
        val app = koinApplication { modules(dataModule, platform) }
        val scope = app.koin.get<CoroutineScope>(ApplicationScope)

        app.close()

        assertThat(scope.isActive).isFalse()
    }

    private object PlainCipher : TokenCipher {
        override fun encrypt(plain: ByteArray) = Sealed(ByteArray(12), plain)
        override fun decrypt(sealed: Sealed) = sealed.ciphertext
    }

    private object NoLanguage : LanguageRepository {
        override fun current(): String? = null
        override fun set(languageTag: String?) = Unit
    }
}
