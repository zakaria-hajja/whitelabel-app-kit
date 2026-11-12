package com.whitelabel.core.shell.brand

import android.os.Build
import android.util.Log
import com.whitelabel.core.analytics.Analytics
import com.whitelabel.core.analytics.CrashReporter
import com.whitelabel.core.analytics.LoggingAnalytics
import com.whitelabel.core.analytics.LoggingCrashReporter
import com.whitelabel.core.branding.BrandConfig
import com.whitelabel.core.branding.Environment
import com.whitelabel.core.consent.ConsentPort
import com.whitelabel.core.consent.FakeConsentPort
import com.whitelabel.core.consent.consentModule
import com.whitelabel.core.data.di.androidDataModule
import com.whitelabel.core.data.di.dataModule
import com.whitelabel.core.domain.remote.DefaultsOnlyRemoteValues
import com.whitelabel.core.domain.remote.RemoteValues
import com.whitelabel.core.firebase.FirebaseMode
import com.whitelabel.core.firebase.firebaseModule
import com.whitelabel.core.network.EngineOverride
import com.whitelabel.core.network.ModuleContributor
import com.whitelabel.core.network.NetworkConfig
import com.whitelabel.core.network.defaultHttpEngine
import com.whitelabel.core.network.di.networkModule
import com.whitelabel.core.shell.AppDefinition
import com.whitelabel.core.shell.model.shellModelModule
import com.whitelabel.core.shell.toSpec
import com.whitelabel.core.shell.diagnostics.BrandPreviewViewModel
import com.whitelabel.core.shell.diagnostics.HealthDataSource
import com.whitelabel.core.shell.diagnostics.KtorHealthDataSource
import io.ktor.client.engine.HttpClientEngine
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Base URL for apps without a backend: `.invalid` never resolves (RFC 2606), and nothing calls it. */
internal const val NO_BACKEND_BASE_URL = "https://no-backend.invalid/"

/** Qualifies the Boolean that is true when a contributor (the mock backend) replaced the HTTP engine. */
internal const val USING_MOCK_API = "usingMockApi"

/** What the shell provides: the app's definition and brand, networking setup, analytics and its own ViewModels. */
internal fun shellModule(definition: AppDefinition) = module {
    single { definition }
    single<BrandConfig> { brandConfigFromResources(androidContext()) }
    single {
        val brand = get<BrandConfig>()
        NetworkConfig(
            baseUrl = brand.apiBaseUrl ?: NO_BACKEND_BASE_URL,
            tenant = brand.tenant,
            userAgent = "${brand.applicationId}/${brand.versionName} (Android ${Build.VERSION.RELEASE})",
            logger = if (brand.environment == Environment.PRODUCTION) null else { line -> Log.d("Http", line) },
        )
    }
    single<HttpClientEngine> { getOrNull<EngineOverride>()?.engine() ?: defaultHttpEngine() }
    single(named(USING_MOCK_API)) { getOrNull<EngineOverride>() != null }
    single<Analytics> { definition.analytics ?: LoggingAnalytics { line -> Log.d("Analytics", line) } }
    single<CrashReporter> {
        definition.crashReporter ?: LoggingCrashReporter { message, throwable -> Log.e("Crash", message, throwable) }
    }
    single<RemoteValues> { DefaultsOnlyRemoteValues }
    single<HealthDataSource> { KtorHealthDataSource(get()) }
    viewModel { BrandPreviewViewModel(get(), get(), get(named(USING_MOCK_API))) }
}

/**
 * Every Koin module an Android app starts with: the core layers, the shell, the definition's
 * modules (features, then the app's own), then each classpath [contributors]' (the mock backend).
 * Koin keeps the last binding of a type: an app can replace a shared binding, and a contributor's
 * wins over everything.
 */
internal fun foundationModules(
    definition: AppDefinition,
    contributors: List<ModuleContributor>,
    firebase: FirebaseWiring = FirebaseWiring.OFF,
): List<Module> =
    listOf(networkModule, dataModule, androidDataModule, shellModule(definition), shellModelModule(definition.toSpec())) +
        definition.koinModules +
        firebaseModules(definition, firebase) +
        contributors.flatMap { it.modules() } +
        // Fake mode answers consent itself, after the contributors, so no real form is ever shown.
        fakeConsentModules(definition, firebase)

private fun firebaseModules(definition: AppDefinition, wiring: FirebaseWiring): List<Module> =
    if (!definition.usesFirebase) emptyList()
    else listOf(consentModule(), firebaseModule(wiring.mode, wiring.debugBuild, wiring.debugAnalytics))

private fun fakeConsentModules(definition: AppDefinition, wiring: FirebaseWiring): List<Module> =
    if (definition.usesFirebase && wiring.mode == FirebaseMode.FAKE) {
        listOf(module { single<ConsentPort> { FakeConsentPort(wiring.fakeConsent) } })
    } else {
        emptyList()
    }
