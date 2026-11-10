package com.whitelabel.core.shell

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraphBuilder

/** A bottom-bar destination. [route] is a `@Serializable` route object registered by the entry's graph. */
data class Tab(
    val route: Any,
    @StringRes val label: Int,
    val icon: ImageVector,
)

/**
 * One feature in an app. [routes] registers the feature's navigation graph; [tab], when set, puts
 * it in the bottom bar; [flagKey], when set, lets the runtime feature flags switch it off.
 */
class FeatureEntry(
    val id: String,
    val flagKey: String? = null,
    val tab: Tab? = null,
    val routes: NavGraphBuilder.(AppNavigator) -> Unit,
)

/** Everything an app contains, built with [appDefinition]. */
class AppDefinition internal constructor(
    val entries: List<FeatureEntry>,
    /** `null` means the first tab. */
    val startTabRoute: Any?,
) {
    val tabs: List<Tab> get() = entries.mapNotNull { it.tab }
}

class AppDefinitionBuilder internal constructor() {
    private val entries = mutableListOf<FeatureEntry>()
    private var startTabRoute: Any? = null

    fun add(entry: FeatureEntry) {
        entries += entry
    }

    fun startTab(route: Any) {
        startTabRoute = route
    }

    internal fun build(): AppDefinition {
        val duplicates = entries.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "Feature added more than once: ${duplicates.joinToString()}" }
        val tabRoutes = entries.mapNotNull { it.tab?.route }
        require(tabRoutes.isNotEmpty()) { "An app definition needs at least one tab" }
        startTabRoute?.let { start ->
            require(start in tabRoutes) { "Start tab $start is not one of the tabs: ${tabRoutes.joinToString()}" }
        }
        return AppDefinition(entries.toList(), startTabRoute)
    }
}

/** Declares an app's features, e.g. `appDefinition { notes(); diagnostics(); startTab(NotesRoute) }`. */
fun appDefinition(block: AppDefinitionBuilder.() -> Unit): AppDefinition =
    AppDefinitionBuilder().apply(block).build()
