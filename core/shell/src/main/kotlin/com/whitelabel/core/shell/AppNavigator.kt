package com.whitelabel.core.shell

/** Navigation the shell offers features, so features never import each other's routes. */
interface AppNavigator {
    /** Switches to the bottom-bar tab whose root is [route]. */
    fun toTab(route: Any)

    /** Whether the tab whose root is [route] is in this app and switched on. */
    fun canOpenTab(route: Any): Boolean

    /** Pushes [route], a destination the calling feature registered. */
    fun navigate(route: Any)

    fun back()

    /** Opens [url] outside the app (browser or custom tab). */
    fun openUrl(url: String)
}
