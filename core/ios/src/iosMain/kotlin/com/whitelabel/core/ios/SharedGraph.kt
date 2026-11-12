package com.whitelabel.core.ios

import androidx.lifecycle.SavedStateHandle
import com.whitelabel.core.ads.AdFree
import com.whitelabel.core.ads.AdPlacement
import com.whitelabel.core.ads.AdTabOwner
import com.whitelabel.core.ads.AdsBuilder
import com.whitelabel.core.ads.AdsController
import com.whitelabel.core.ads.AdsPlatform
import com.whitelabel.core.ads.AdsWiring
import com.whitelabel.core.ads.TrackingPort
import com.whitelabel.core.ads.adsModule
import com.whitelabel.core.ads.adsWiring
import com.whitelabel.core.ads.checkAdPlacementTabs
import com.whitelabel.core.analytics.Analytics
import com.whitelabel.core.analytics.CrashReporter
import com.whitelabel.core.analytics.LoggingAnalytics
import com.whitelabel.core.analytics.LoggingCrashReporter
import com.whitelabel.core.branding.BrandConfig
import com.whitelabel.core.branding.Environment
import com.whitelabel.core.branding.hasBackend
import com.whitelabel.core.consent.Consent
import com.whitelabel.core.consent.ConsentPort
import com.whitelabel.core.consent.FakeConsentPort
import com.whitelabel.core.consent.UmpResult
import com.whitelabel.core.consent.DebugConsent
import com.whitelabel.core.consent.consentModule
import com.whitelabel.core.consent.fakeConsentResult
import com.whitelabel.core.data.di.ApplicationScope
import com.whitelabel.core.data.di.dataModule
import com.whitelabel.core.datastore.StorePaths
import com.whitelabel.core.datastore.TokenCipher
import com.whitelabel.core.domain.LanguageRepository
import com.whitelabel.core.firebase.CollectionSwitch
import com.whitelabel.core.firebase.FirebaseMode
import com.whitelabel.core.firebase.RemoteConfigSource
import com.whitelabel.core.firebase.firebaseModule
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
import org.koin.core.qualifier.named
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

    /** What Swift's services hand over before [start]; kept across [resetForTests] like [launch]. */
    private class FirebaseServices(
        val source: RemoteConfigSource,
        val analytics: Analytics,
        val crashReporter: CrashReporter,
        val collection: CollectionSwitch,
    )

    private var consentPort: ConsentPort? = null
    private var firebaseServices: FirebaseServices? = null
    private var firebaseDeclared = false
    private var debugFake = false
    private var debugBuild = false
    private var debugAnalytics = false
    private var fakeConsent: UmpResult? = fakeConsentResult(DebugConsent.DEFAULT_FAKE_STATE)
    private var mode = FirebaseMode.OFF

    /** The app definition's placements (`AppDefinition(ads:)`), or null for an app without ads. */
    private var adPlacements: List<AdPlacement>? = null
    /** The routes among the placements' `underTab`s that the sign-in gate's screens own. */
    private var adGateRoutes: List<String> = emptyList()
    /** What `AdsService` hands over in `on` mode: the ATT port (or `NoTracking`); null when it installed nothing. */
    private var adsTracking: TrackingPort? = null
    private var ads = AdsWiring.OFF

    val isStarted: Boolean get() = KoinPlatform.getKoinOrNull() != null

    /** Registers a debug-only module (the fake backend, via `IosMock.enable()`). */
    fun registerContributor(contributor: ModuleContributor) {
        if (contributors.none { it::class == contributor::class }) contributors += contributor
    }

    /**
     * Hands over the platform's consent form (UMP). Call before [start]. Firebase and ads both hand it
     * over in `on` mode; the graph has a consent whenever the app uses either (or a port was given).
     */
    fun useConsent(port: ConsentPort) {
        consentPort = port
    }

    /** Hands over the platform's Firebase pieces. Call before [start]; it also declares Firebase. */
    fun useFirebase(source: RemoteConfigSource, analytics: Analytics, crashReporter: CrashReporter, collection: CollectionSwitch) {
        firebaseServices = FirebaseServices(source, analytics, crashReporter, collection)
        firebaseDeclared = true
    }

    /** The app's definition has a Firebase service (as Android's `firebase()`), even if it installs nothing in `off` mode. */
    fun declareFirebase() {
        firebaseDeclared = true
    }

    /**
     * The app's banner placements (`AppDefinition(ads:)`, Android's `ads { }`): the app uses ads.
     * [gateRoutes] are the placements' routes that the sign-in gate's screens own (Swift asks the gate
     * feature), so a placement there fails as on Android. Call before [start], which checks them
     * against the app's ads settings.
     */
    fun declareAds(placements: List<AdPlacement>, gateRoutes: List<String> = emptyList()) {
        adPlacements = placements
        adGateRoutes = gateRoutes
    }

    /**
     * Hands over what `AdsService` installs in `on` mode: the tracking prompt ([tracking]: ATT, or
     * `NoTracking` with `ads.ios.requestTracking` off). Without it, ads on fails at [start].
     */
    fun useAds(tracking: TrackingPort) {
        adsTracking = tracking
    }

    /** The banner rules, or null in an app without ads. With ads off in this build every banner stays hidden. */
    fun adsController(): AdsController? = koin.getOrNull()

    /** The ad-free window, for an app's rewards; null in an app without ads. */
    fun adFree(): AdFree? = koin.getOrNull()

    /** The banner unit of each placement in the started graph (tests). */
    internal fun adsUnits(): Map<String, String> = ads.units

    /**
     * Debug launch arguments, called before [start] in debug builds only (so it also marks the build
     * as debug): [fake] runs Firebase as the in-memory fake, [analytics] lets a debug build collect, and
     * [fakeConsent] (`obtained`, `declined`, `failed`, `notRequired`) is what the fake consent answers.
     */
    fun useFirebaseDebug(fake: Boolean, analytics: Boolean, fakeConsent: String) {
        debugBuild = true
        debugFake = fake
        debugAnalytics = analytics
        this.fakeConsent = fakeConsentResult(fakeConsent)
    }

    /** Forgets what Swift handed over, for Firebase and ads (tests). */
    fun clearFirebaseServices() {
        adPlacements = null
        adGateRoutes = emptyList()
        adsTracking = null
        consentPort = null
        firebaseServices = null
        firebaseDeclared = false
        debugFake = false
        debugBuild = false
        debugAnalytics = false
        fakeConsent = fakeConsentResult(DebugConsent.DEFAULT_FAKE_STATE)
    }

    /** How Firebase runs in the started graph. */
    fun firebaseMode(): FirebaseMode = mode

    /** The app's consent state; only an app with a Firebase service has one to gather. */
    fun consent(): Consent = koin.get()

    /** The app's remote values: Firebase's in `on`/`fake` mode, the caller's defaults otherwise. */
    fun remoteValues(): RemoteValues = koin.get()

    /** The app's consent, or null when the app has neither a Firebase service nor ads (nothing to ask). */
    fun consentOrNull(): Consent? = koin.getOrNull()

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
        val firebaseMode = firebaseModeFor(platform.firebase, services = firebaseServices != null, declared = firebaseDeclared, fakeMarker = debugFake)
        val adsWiring = adsWiringFor(platform, definition)
        launch = Launch(platform, definition, appModules, useMockApi)
        mode = firebaseMode
        ads = adsWiring
        Texts.pinLaunchLanguage()
        startKoin {
            modules(
                listOf(networkModule, dataModule, shellModelModule(definition), platformModule(platform)) +
                    listOf(authenticationModule, settingsModule, profileModule, homeModule, catalogModule) +
                    appModules +
                    consentModules() +
                    firebaseModules(firebaseMode) +
                    adsModules(adsWiring) +
                    (if (useMockApi) contributors.flatMap { it.modules() } else emptyList()) +
                    fakeOverrides(firebaseMode),
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

    /**
     * Checks the app's placements (names, tabs) and its ads settings, as Android's shell does, and
     * resolves each placement's unit.
     */
    private fun adsWiringFor(platform: IosPlatform, definition: DefinitionSpec): AdsWiring {
        val placements = adPlacements.orEmpty()
        // The builder applies Android's name rules (letters, digits, '_' and '-'; no repeats).
        AdsBuilder().apply { placements.forEach { placement(it.name, it.underTab) } }
        checkAdPlacementTabs(
            placements,
            ownerOf = { route ->
                val tab = definition.features.firstOrNull { it.tabRoute != null && it.tabRoute == route }
                val gate = definition.features.firstOrNull { it.isGate }?.takeIf { route in adGateRoutes }
                (tab ?: gate)?.let { AdTabOwner(it.id, it.isGate) }
            },
            routeName = { it.toString() },
        )
        return adsWiring(
            usesAds = adPlacements != null,
            placements = placements.map { it.name },
            declared = platform.adsDeclared,
            mode = platform.ads,
            units = platform.adsUnits,
            sdkLinked = adsTracking != null,
            sdkMissing = "ads are on but AdsService() isn't in the app's services",
            debugBuild = platform.debugBuild,
            platform = AdsPlatform.IOS,
        )
    }

    /** Firebase and ads both wait for the user's consent: the graph has one when the app uses either. */
    private fun consentModules(): List<Module> {
        val port = consentPort
        if (!firebaseDeclared && adPlacements == null && port == null) return emptyList()
        return listOf(module { if (port != null) single<ConsentPort> { port } }, consentModule())
    }

    /**
     * An app with ads always gets the controller, so its banners and `adFree()` resolve even in a
     * build with ads off (`ads.enabled=debug` in Release), where every banner stays hidden.
     */
    private fun adsModules(wiring: AdsWiring): List<Module> {
        if (adPlacements == null) return emptyList()
        val tracking = adsTracking
        return listOf(
            module { if (tracking != null) single<TrackingPort> { tracking } },
            adsModule(wiring.enabled, wiring.units),
        )
    }

    private fun firebaseModules(mode: FirebaseMode): List<Module> {
        if (!firebaseDeclared) return emptyList()
        val services = firebaseServices
        return listOfNotNull(
            module {
                if (services != null) {
                    single<RemoteConfigSource> { services.source }
                    single<Analytics>(named("firebase")) { services.analytics }
                    single<CrashReporter>(named("firebase")) { services.crashReporter }
                    single<CollectionSwitch> { services.collection }
                }
            },
            // Fake mode's Firebase module goes last (fakeOverrides), not here.
            if (mode == FirebaseMode.FAKE) null else firebaseModule(mode, debugBuild, debugAnalytics),
        )
    }

    /** Fake mode's bindings go last, so a registered service can never replace them. */
    private fun fakeOverrides(mode: FirebaseMode): List<Module> {
        if (mode != FirebaseMode.FAKE) return emptyList()
        val answer = fakeConsent
        return listOf(
            firebaseModule(FirebaseMode.FAKE, debugBuild, debugAnalytics),
            module { single<ConsentPort> { FakeConsentPort(answer) } },
        )
    }

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
