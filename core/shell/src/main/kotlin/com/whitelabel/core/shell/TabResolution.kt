package com.whitelabel.core.shell

/**
 * Tabs to show. An entry without a flag key is always visible; one with a key uses the remote
 * value when present, otherwise whether the key is in [defaultFeatures] (off when absent).
 */
internal fun AppDefinition.visibleTabs(remoteFlags: Map<String, Boolean>, defaultFeatures: Set<String>): List<Tab> =
    entries.filter { entry ->
        val key = entry.flagKey ?: return@filter true
        remoteFlags[key] ?: (key in defaultFeatures)
    }.mapNotNull { it.tab }

/** The declared start tab if visible, else the first visible tab, else `null` (nothing to show). */
internal fun AppDefinition.resolveStartTab(visible: List<Tab>): Tab? =
    visible.firstOrNull { it.route == startTabRoute } ?: visible.firstOrNull()
