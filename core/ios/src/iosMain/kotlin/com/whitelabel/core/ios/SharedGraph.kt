package com.whitelabel.core.ios

import androidx.lifecycle.SavedStateHandle
import com.whitelabel.core.analytics.Analytics
import com.whitelabel.core.analytics.CrashReporter
import com.whitelabel.core.analytics.LoggingAnalytics
import com.whitelabel.core.analytics.LoggingCrashReporter
import com.whitelabel.core.branding.BrandConfig
import com.whitelabel.core.branding.Environment
import com.whitelabel.core.branding.hasBackend
import com.whitelabel.core.data.di.ApplicationScope
import com.whitelabel.core.data.di.dataModule
import com.whitelabel.core.datastore.StorePaths
import com.whitelabel.core.datastore.TokenCipher
import com.whitelabel.core.domain.LanguageRepository
import com.whitelabel.core.domain.remote.DefaultsOnlyRemoteValues
import com.whitelabel.core.domain.remote.RemoteValues
import com.whitelabel.core.network.EngineOverride
import com.whitelabel.core.network.ModuleContributor
import com.whitelabel.core.network.NetworkConfig
import com.whitelabel.core.network.defaultHttpEngine
import com.whitelabel.core.network.di.networkModule
import com.whitelabel.core.shell.model.DefinitionSpec
import com.whitelabel.core.shell.model.ShellViewModel
import com.whitelabel.core.shell.model.shellModelModule
import com.whitelabel.feature.authentication.authenticationModule
import com.whitelabel.feature.authentication.presentation.forgot.ForgotPasswordViewModel
import com.whitelabel.feature.authentication.presentation.login.LoginViewModel
import com.whitelabel.feature.authentication.presentation.register.RegisterViewModel
import com.whitelabel.feature.authentication.presentation.reset.ResetPasswordViewModel
import com.whitelabel.feature.catalog.CatalogOptions
import com.whitelabel.feature.catalog.catalogModule
import com.whitelabel.feature.catalog.presentation.detail.CatalogDetailViewModel
import com.whitelabel.feature.catalog.presentation.list.CatalogListViewModel
import com.whitelabel.feature.home.homeModule
import com.whitelabel.feature.home.presentation.HomeViewModel
import com.whitelabel.feature.profile.presentation.password.ChangePasswordViewModel
import com.whitelabel.feature.profile.presentation.profile.ProfileViewModel
import com.whitelabel.feature.profile.profileModule
import com.whitelabel.feature.settings.presentation.SettingsViewModel
import com.whitelabel.feature.settings.settingsModule
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.koin.core.Koin
import org.koin.mp.KoinPlatform
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import platform.UIKit.UIDevice

/** Base URL for apps without a backend: `.invalid` never resolves (RFC 2606), and nothing calls it. */
private const val NO_BACKEND_BASE_URL = "https://no-backend.invalid/"

/**
 * The iOS app's way into the shared code: start the graph once, then get each shared ViewModel
 * through its typed accessor (Swift can't use Koin's reified functions). Every failure Swift must
 * handle is declared with @Throws, since an undeclared Kotlin exception aborts the process.
 */
object SharedGraph {

    private class Launch(val platform: IosPlatform, val definition: DefinitionSpec, val appModules: List<Module>, val useMockApi: Boolean)

    private var launch: Launch? = null
    private val contributors = mutableListOf<ModuleContributor>()

    val isStarted: Boolean get() = KoinPlatform.getKoinOrNull() != null

    /** Registers a debug-only module (the fake backend, via `IosMock.enable()`). */
    fun registerContributor(contributor: ModuleContributor) {
        if (contributors.none { it::class == contributor::class }) contributors += contributor
    }

    /**
     * Starts the graph for [definition], once; later calls are ignored. Fails, with the rule's
     * message, on an invalid definition, on backend features without a backend URL, and on
     * [useMockApi] without the fake backend linked.
     */
    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    fun start(platform: IosPlatform, definition: DefinitionSpec, appModules: List<Module>, useMockApi: Boolean) {
        if (isStarted) return
        definition.validationError()?.let { throw IllegalArgumentException(it) }
        definition.backendProblem(platform.brand.hasBackend)?.let { throw IllegalStateException(it) }
        check(!useMockApi || contributors.isNotEmpty()) {
            "The fake backend isn't linked: build the framework with -Pwhitelabel.mockApi=true and call IosMock.enable() first"
        }
        launch = Launch(platform, definition, appModules, useMockApi)
        Texts.pinLaunchLanguage()
        startKoin {
            modules(
                listOf(networkModule, dataModule, shellModelModule(definition), platformModule(platform)) +
                    listOf(authenticationModule, settingsModule, profileModule, homeModule, catalogModule) +
                    appModules +
                    if (useMockApi) contributors.flatMap { it.modules() } else emptyList(),
            )
        }
    }

    /** Stops the graph and waits for its stores to close (tests; then start again). */
    fun stop() {
        val job = KoinPlatform.getKoinOrNull()?.getOrNull<CoroutineScope>(ApplicationScope)?.coroutineContext?.job
        stopKoin()
        job?.let { runBlocking { it.join() } }
    }

    /** A fresh graph with the same arguments, as Android's FreshAppRule gives each test. */
    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    fun resetForTests() {
        val last = checkNotNull(launch) { "SharedGraph.start was never called" }
        stop()
        start(last.platform, last.definition, last.appModules, last.useMockApi)
    }

    fun shellViewModel(): ShellViewModel = koin.get { parametersOf(SavedStateHandle()) }
    fun settingsViewModel(): SettingsViewModel = koin.get()
    fun homeViewModel(): HomeViewModel = koin.get()
    fun profileViewModel(): ProfileViewModel = koin.get()
    fun changePasswordViewModel(): ChangePasswordViewModel = koin.get()
    fun loginViewModel(email: String?): LoginViewModel = koin.get { parametersOf(savedState("email" to email)) }
    fun registerViewModel(): RegisterViewModel = koin.get { parametersOf(SavedStateHandle()) }
    fun forgotPasswordViewModel(email: String?): ForgotPasswordViewModel = koin.get { parametersOf(savedState("email" to email)) }
    fun resetPasswordViewModel(token: String): ResetPasswordViewModel = koin.get { parametersOf(savedState("token" to token)) }
    fun catalogListViewModel(options: CatalogOptions): CatalogListViewModel = koin.get { parametersOf(options) }
    fun catalogDetailViewModel(id: String): CatalogDetailViewModel = koin.get { parametersOf(savedState("id" to id)) }

    private val koin: Koin get() = KoinPlatform.getKoin()

    /** The keys are the Android routes' argument names, which the shared ViewModels read. */
    private fun savedState(vararg pairs: Pair<String, String?>) =
        SavedStateHandle(pairs.filter { it.second != null }.associate { it.first to it.second })

    private fun platformModule(platform: IosPlatform) = module {
        single<TokenCipher> { platform.tokenCipher }
        single<LanguageRepository> { platform.language }
        single<StorePaths> { platform.storePaths }
        single<BrandConfig> { platform.brand }
        single {
            val brand = platform.brand
            NetworkConfig(
                baseUrl = brand.apiBaseUrl ?: NO_BACKEND_BASE_URL,
                tenant = brand.tenant,
                userAgent = "${brand.applicationId}/${brand.versionName} (iOS ${UIDevice.currentDevice.systemVersion})",
                logger = if (brand.environment == Environment.PRODUCTION) null else platform.logger,
            )
        }
        single<HttpClientEngine> { getOrNull<EngineOverride>()?.engine() ?: defaultHttpEngine() }
        single<RemoteValues> { DefaultsOnlyRemoteValues }
        single<Analytics> { LoggingAnalytics(platform.logger) }
        single<CrashReporter> { LoggingCrashReporter { message, throwable -> platform.logger("$message: $throwable") } }
    }
}
