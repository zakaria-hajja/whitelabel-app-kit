package com.whitelabel.core.shell

import com.whitelabel.core.shell.model.resolveStartTab
import com.whitelabel.core.shell.model.visibleTabRoutes

/** Android's [Tab]s for the shared model's visible routes, in declaration order. */
internal fun AppDefinition.visibleTabs(remoteFlags: Map<String, Boolean>, defaultFeatures: Set<String>): List<Tab> {
    val routes = toSpec().visibleTabRoutes(remoteFlags, defaultFeatures)
    return tabs.filter { it.routeId in routes }
}

/** The shared model's start tab among [visible], as Android's [Tab]. */
internal fun AppDefinition.resolveStartTab(visible: List<Tab>): Tab? {
    val route = toSpec().resolveStartTab(visible.map { it.routeId })
    return visible.firstOrNull { it.routeId == route }
}
