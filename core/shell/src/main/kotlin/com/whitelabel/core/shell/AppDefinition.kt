package com.whitelabel.core.shell

import com.whitelabel.core.designsystem.theme.StylePack
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraphBuilder
import com.whitelabel.core.analytics.Analytics
import com.whitelabel.core.analytics.CrashReporter

/** A bottom-bar destination. [route] is a `@Serializable` route object registered by the entry's graph. */
data class Tab(
    val route: Any,
    @StringRes val label: Int,
    val icon: ImageVector,
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
 * the feature calls endpoints that need a session.
 */
class FeatureEntry(
    val id: String,
    val flagKey: String? = null,
    val tab: Tab? = null,
    val requiresSignIn: Boolean = false,
    val gate: Gate? = null,
    /** The feature calls the backend, so the app needs `api.*` base URLs. Implied by [requiresSignIn] and [gate]. */
    val requiresBackend: Boolean = false,
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
) {
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

    /** Records unexpected failures with [reporter] instead of logcat. */
    fun crashReporter(reporter: CrashReporter) {
        crashReporter = reporter
    }

    internal fun build(): AppDefinition {
        val duplicates = entries.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "Feature added more than once: ${duplicates.joinToString()}" }
        val tabRoutes = entries.mapNotNull { it.tab?.route }
        require(tabRoutes.isNotEmpty()) { "An app definition needs at least one tab" }
        startTabRoute?.let { start ->
            require(start in tabRoutes) { "Start tab $start is not one of the tabs: ${tabRoutes.joinToString()}" }
        }
        val gates = entries.filter { it.gate != null }
        require(gates.size <= 1) { "Only one feature may gate the app: ${gates.joinToString { it.id }}" }
        val needSignIn = entries.filter { it.requiresSignIn }
        require(needSignIn.isEmpty() || gates.isNotEmpty()) {
            "${needSignIn.joinToString { it.id }} need sign-in: add auth() to the app definition"
        }
        return AppDefinition(entries.toList(), startTabRoute, analytics, crashReporter, stylePack)
    }
}

/**
 * Why this definition can't run without a backend, or `null` if it can: features that sign in or
 * call the API need the app's `api.*` base URLs.
 */
internal fun AppDefinition.backendProblem(hasBackend: Boolean): String? {
    if (hasBackend) return null
    val needing = entries.filter { it.gate != null || it.requiresSignIn || it.requiresBackend }
    if (needing.isEmpty()) return null
    return "${needing.joinToString { it.id }} call the backend: " +
        "set api.debug.baseUrl and api.release.baseUrl in app.properties"
}

/** Declares an app's features, e.g. `appDefinition { auth(); notes(); settings(); startTab(NotesRoute) }`. */
fun appDefinition(block: AppDefinitionBuilder.() -> Unit): AppDefinition =
    AppDefinitionBuilder().apply(block).build()
