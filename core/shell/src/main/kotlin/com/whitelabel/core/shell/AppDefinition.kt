package com.whitelabel.core.shell

import com.whitelabel.core.designsystem.theme.StylePack
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraphBuilder
import com.whitelabel.core.analytics.Analytics
import com.whitelabel.core.analytics.CrashReporter
import org.koin.core.module.Module
import com.whitelabel.core.shell.model.DefinitionSpec
import com.whitelabel.core.shell.model.FeatureSpec

/** A bottom-bar destination. [route] is a `@Serializable` route object registered by the entry's graph. */
/**
 * A bottom-bar tab. [routeId] is the platform-neutral id the shared shell model and iOS use (the
 * foundation's features pass their `<Name>Routes` constant); it defaults to the route's class name.
 */
data class Tab(
    val route: Any,
    @StringRes val label: Int,
    val icon: ImageVector,
    val routeId: String = route::class.simpleName ?: route.toString(),
)

/** Makes the whole app sign-in only: signed out, the app shows only this entry's graph, starting at [startRoute]. */
class Gate(val startRoute: Any)

/** What the shell hands a feature while it registers its routes. */
class FeatureScope internal constructor(
    val navigator: AppNavigator,
    /** The app's deep-link scheme, for `navDeepLink { uriPattern = "$deepLinkScheme://…" }`. */
    val deepLinkScheme: String,
)

/**
 * One feature in an app. [routes] registers the feature's navigation graph; [tab], when set, puts
 * it in the bottom bar; [flagKey], when set, lets the runtime feature flags switch it off.
 * [requiresSignIn] makes the definition fail to build without a [gate] entry (`auth()`), because
 * the feature calls endpoints that need a session. [modules] are the feature's Koin bindings (its
 * ViewModels and data sources), loaded when the app starts.
 */
class FeatureEntry(
    val id: String,
    val flagKey: String? = null,
    val tab: Tab? = null,
    val requiresSignIn: Boolean = false,
    val gate: Gate? = null,
    /** The feature calls the backend, so the app needs `api.*` base URLs. Implied by [requiresSignIn] and [gate]. */
    val requiresBackend: Boolean = false,
    val modules: List<Module> = emptyList(),
    val routes: NavGraphBuilder.(FeatureScope) -> Unit,
)

/** Everything an app contains, built with [appDefinition]. */
class AppDefinition internal constructor(
    internal val entries: List<FeatureEntry>,
    /** `null` means the first tab. */
    internal val startTabRoute: Any?,
    internal val analytics: Analytics?,
    internal val crashReporter: CrashReporter?,
    /** The look and motion the shell applies; [StylePack.Material] unless the app calls `style()`. */
    internal val stylePack: StylePack = StylePack.Material,
    /** The app's own Koin bindings, from `modules(...)`. */
    internal val appModules: List<Module> = emptyList(),
) {
    /**
     * Every Koin module the definition brings, once each: each entry's, then the app's own. Koin
     * keeps the last binding of a type, so an app's binding replaces a feature's.
     */
    internal val koinModules: List<Module> get() = (entries.flatMap { it.modules } + appModules).distinct()

    internal val tabs: List<Tab> get() = entries.mapNotNull { it.tab }

    /** The entry that gates the app (`auth()`), if any. */
    internal val gateEntry: FeatureEntry? get() = entries.firstOrNull { it.gate != null }
}

class AppDefinitionBuilder internal constructor() {
    private val entries = mutableListOf<FeatureEntry>()
    private var startTabRoute: Any? = null
    private var analytics: Analytics? = null
    private var crashReporter: CrashReporter? = null
    private var stylePack: StylePack = StylePack.Material
    private val appModules = mutableListOf<Module>()

    fun add(entry: FeatureEntry) {
        entries += entry
    }

    fun startTab(route: Any) {
        startTabRoute = route
    }

    /** Applies [pack]'s look and motion to the whole app; the last call wins. */
    fun style(pack: StylePack) {
        stylePack = pack
    }

    /** Sends analytics to [analytics] instead of logcat. */
    fun analytics(analytics: Analytics) {
        this.analytics = analytics
    }

    /** Adds the app's own Koin bindings (its repositories, `remindersModule(config)`, …). */
    fun modules(vararg modules: Module) {
        appModules += modules
    }

    /** Records unexpected failures with [reporter] instead of logcat. */
    fun crashReporter(reporter: CrashReporter) {
        crashReporter = reporter
    }

    internal fun build(): AppDefinition {
        val definition = AppDefinition(entries.toList(), startTabRoute, analytics, crashReporter, stylePack, appModules.toList())
        definition.toSpec().validate()
        return definition
    }
}

/**
 * Why this definition can't run without a backend, or `null` if it can: features that sign in or
 * call the API need the app's `api.*` base URLs.
 */
internal fun AppDefinition.backendProblem(hasBackend: Boolean): String? = toSpec().backendProblem(hasBackend)

/** Declares an app's features, e.g. `appDefinition { auth(); notes(); settings(); startTab(NotesRoute) }`. */
fun appDefinition(block: AppDefinitionBuilder.() -> Unit): AppDefinition =
    AppDefinitionBuilder().apply(block).build()

/** The platform-neutral form of this definition, which the shared shell model works on. */
internal fun AppDefinition.toSpec(): DefinitionSpec = DefinitionSpec(
    features = entries.map {
        FeatureSpec(
            id = it.id,
            flagKey = it.flagKey,
            tabRoute = it.tab?.routeId,
            requiresSignIn = it.requiresSignIn,
            isGate = it.gate != null,
            requiresBackend = it.requiresBackend,
        )
    },
    startTabRoute = startTabRoute?.let { start -> tabs.firstOrNull { it.route == start }?.routeId ?: start::class.simpleName },
)
