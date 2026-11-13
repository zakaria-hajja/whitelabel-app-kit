package com.whitelabel.core.shell.brand

import android.os.Build
import android.util.Log
import com.whitelabel.core.ads.AdsWiring
import com.whitelabel.core.ads.adsModule
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
 * modules (features, then the app's own), consent, Firebase and ads when the app uses them, then the
 * build type's [buildTypeModules] (Debug: the guest account), then each classpath [contributors]'
 * (the mock backend, the Google SDK modules). Koin keeps the last binding of a type: an app can
 * replace a shared binding, and a contributor's wins over everything (the fake's guest account over
 * the Debug one).
 */
internal fun foundationModules(
    definition: AppDefinition,
    contributors: List<ModuleContributor>,
    firebase: FirebaseWiring = FirebaseWiring.OFF,
    ads: AdsWiring = AdsWiring.OFF,
    buildTypeModules: List<Module> = emptyList(),
): List<Module> =
    listOf(networkModule, dataModule, androidDataModule, shellModule(definition), shellModelModule(definition.toSpec())) +
        definition.koinModules +
        (if (definition.needsConsent) listOf(consentModule()) else emptyList()) +
        firebaseModules(definition, firebase) +
        // An app with ads { } always gets the controller, so its AdBanner and AdFree calls resolve even
        // in a build with ads off (ads.enabled=debug in release), where every banner stays hidden.
        (if (definition.usesAds) listOf(adsModule(ads.enabled, ads.units)) else emptyList()) +
        buildTypeModules +
        contributors.flatMap { it.modules() } +
        fakeOverrides(definition, firebase)

/** Fake mode's Firebase module isn't here: it goes last ([fakeOverrides]). */
private fun firebaseModules(definition: AppDefinition, wiring: FirebaseWiring): List<Module> = when {
    !definition.usesFirebase || wiring.mode == FirebaseMode.FAKE -> emptyList()
    else -> listOf(firebaseModule(wiring.mode, wiring.debugBuild, wiring.debugAnalytics))
}

/**
 * Fake mode's bindings (fake remote values, fake consent that shows no real form) go last, after the
 * contributors, so a linked Firebase or UMP contributor can never replace them.
 */
private fun fakeOverrides(definition: AppDefinition, wiring: FirebaseWiring): List<Module> =
    if (definition.usesFirebase && wiring.mode == FirebaseMode.FAKE) {
        listOf(
            firebaseModule(FirebaseMode.FAKE, wiring.debugBuild, wiring.debugAnalytics),
            module { single<ConsentPort> { FakeConsentPort(wiring.fakeConsent) } },
        )
    } else {
        emptyList()
    }
