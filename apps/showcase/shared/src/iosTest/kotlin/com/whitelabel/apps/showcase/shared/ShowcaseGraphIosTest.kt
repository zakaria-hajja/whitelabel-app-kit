package com.whitelabel.apps.showcase.shared

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.whitelabel.core.analytics.Analytics
import com.whitelabel.core.analytics.AnalyticsEvent
import com.whitelabel.core.branding.BrandConfig
import com.whitelabel.core.branding.Environment
import com.whitelabel.core.branding.RuntimeDefaults
import com.whitelabel.core.datastore.Sealed
import com.whitelabel.core.datastore.StorePaths
import com.whitelabel.core.datastore.TokenCipher
import com.whitelabel.core.domain.LanguageRepository
import com.whitelabel.core.network.NetworkConfig
import com.whitelabel.feature.authentication.presentation.forgot.ForgotPasswordViewModel
import com.whitelabel.feature.authentication.presentation.login.LoginViewModel
import com.whitelabel.feature.authentication.presentation.register.RegisterViewModel
import com.whitelabel.feature.authentication.presentation.reset.ResetPasswordViewModel
import com.whitelabel.feature.catalog.CatalogOptions
import com.whitelabel.feature.catalog.presentation.detail.CatalogDetailViewModel
import com.whitelabel.feature.catalog.presentation.list.CatalogListViewModel
import com.whitelabel.feature.home.presentation.HomeViewModel
import com.whitelabel.feature.profile.presentation.password.ChangePasswordViewModel
import com.whitelabel.feature.profile.presentation.profile.ProfileViewModel
import com.whitelabel.feature.settings.presentation.SettingsViewModel
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID

/** Every shared ViewModel Showcase uses builds on iOS from the shared modules and a platform module. */
@OptIn(ExperimentalCoroutinesApi::class)
class ShowcaseGraphIosTest {

    private val dir = (NSTemporaryDirectory() + "showcase-graph-" + NSUUID().UUIDString).toPath()

    private val iosFakes = module {
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
                tenant = null, appName = "Showcase", applicationId = "com.whitelabel.showcase", versionName = "1.0",
                environment = Environment.DEVELOPMENT, apiBaseUrl = null, deepLinkScheme = "showcase",
                runtimeDefaults = RuntimeDefaults(supportEmail = null, privacyPolicyUrl = "", termsUrl = "", enabledFeatures = emptySet()),
            )
        }
        single<Analytics> { object : Analytics { override fun track(event: AnalyticsEvent) = Unit } }
    }

    private val app = koinApplication { modules(showcaseSharedModules(iosFakes)) }

    @BeforeTest fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        FileSystem.SYSTEM.createDirectories(dir)
    }

    @AfterTest fun tearDown() {
        app.close()
        FileSystem.SYSTEM.deleteRecursively(dir)
        Dispatchers.resetMain()
    }

    @Test fun `every shared viewmodel resolves`() {
        val viewModels: List<KClass<out ViewModel>> = listOf(
            LoginViewModel::class, RegisterViewModel::class, ForgotPasswordViewModel::class, ResetPasswordViewModel::class,
            SettingsViewModel::class, HomeViewModel::class, CatalogDetailViewModel::class,
            ProfileViewModel::class, ChangePasswordViewModel::class,
        )
        viewModels.forEach { type ->
            val viewModel = app.koin.get<ViewModel>(type) { parametersOf(SavedStateHandle()) }
            assertTrue(type.isInstance(viewModel), type.simpleName)
        }
        val list = app.koin.get<CatalogListViewModel> { parametersOf(CatalogOptions()) }
        assertTrue(list is CatalogListViewModel)
    }
}
