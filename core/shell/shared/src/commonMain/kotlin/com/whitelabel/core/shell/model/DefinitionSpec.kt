package com.whitelabel.core.shell.model

/**
 * One feature of an app, as both platforms' shells see it. [tabRoute] is the shared route id of its
 * tab (e.g. `SettingsRoutes.ROOT`), or null when it has none; [isGate] makes it the whole-app
 * sign-in gate (`auth()`).
 */
data class FeatureSpec(
    val id: String,
    val flagKey: String?,
    val tabRoute: String?,
    val requiresSignIn: Boolean,
    val isGate: Boolean,
    val requiresBackend: Boolean,
)

/** Everything an app contains, platform-neutral. Each platform builds it from its own definition DSL. */
data class DefinitionSpec(val features: List<FeatureSpec>, val startTabRoute: String?) {

    val hasGate: Boolean get() = features.any { it.isGate }

    /**
     * Checks the rules every definition follows and returns it, or throws naming what's wrong.
     * Swift can't catch a Kotlin exception (the process aborts): it calls [validationError] instead.
     */
    @Throws(IllegalArgumentException::class)
    fun validate(): DefinitionSpec {
        val duplicates = features.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "Feature added more than once: ${duplicates.joinToString()}" }
        val tabs = features.mapNotNull { it.tabRoute }
        require(tabs.isNotEmpty()) { "An app definition needs at least one tab" }
        val repeatedTabs = tabs.groupBy { it }.filterValues { it.size > 1 }.keys
        require(repeatedTabs.isEmpty()) { "Tab route added more than once: ${repeatedTabs.joinToString()}" }
        startTabRoute?.let { start -> require(start in tabs) { "Start tab $start is not one of the tabs: ${tabs.joinToString()}" } }
        val gates = features.filter { it.isGate }
        require(gates.size <= 1) { "Only one feature may gate the app: ${gates.joinToString { it.id }}" }
        val needSignIn = features.filter { it.requiresSignIn }
        require(needSignIn.isEmpty() || gates.isNotEmpty()) {
            "${needSignIn.joinToString { it.id }} need sign-in: add auth() to the app definition"
        }
        return this
    }

    /** What [validate] would reject, or null when the definition is valid. */
    fun validationError(): String? = try {
        validate()
        null
    } catch (e: IllegalArgumentException) {
        e.message
    }

    /** Why this definition can't run without a backend, or null if it can. */
    fun backendProblem(hasBackend: Boolean): String? {
        if (hasBackend) return null
        val needing = features.filter { it.isGate || it.requiresSignIn || it.requiresBackend }
        if (needing.isEmpty()) return null
        return "${needing.joinToString { it.id }} call the backend: set api.debug.baseUrl and api.release.baseUrl in app.properties"
    }
}

/**
 * The tab routes to show, in order. A feature without a flag key is always visible; one with a
 * key uses the remote value when present, otherwise whether the key is in [defaultFeatures].
 */
fun DefinitionSpec.visibleTabRoutes(remoteFlags: Map<String, Boolean>, defaultFeatures: Set<String>): List<String> =
    features.filter { feature ->
        val key = feature.flagKey ?: return@filter true
        remoteFlags[key] ?: (key in defaultFeatures)
    }.mapNotNull { it.tabRoute }

/** The declared start tab if visible, else the first visible tab, else null (nothing to show). */
fun DefinitionSpec.resolveStartTab(visible: List<String>): String? =
    visible.firstOrNull { it == startTabRoute } ?: visible.firstOrNull()
