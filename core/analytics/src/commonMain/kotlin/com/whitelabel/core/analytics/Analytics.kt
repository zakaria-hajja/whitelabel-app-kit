package com.whitelabel.core.analytics

private val EVENT_NAME = Regex("^[a-z][a-z0-9_]{0,39}$")

/**
 * One analytics event. [name] is lowercase snake case, at most 40 characters (the strictest common
 * SDK limit). [params] never carry personal data: no email, name, token or free text the user typed.
 */
data class AnalyticsEvent(val name: String, val params: Map<String, String> = emptyMap()) {
    init {
        require(EVENT_NAME.matches(name)) { "Analytics event name must match ${EVENT_NAME.pattern}: '$name'" }
    }
}

/** Where events go. The shell provides a logging one unless the app's definition supplies an SDK. */
interface Analytics {
    fun track(event: AnalyticsEvent)
}

/** Records unexpected failures. The shell provides a logging one unless the app's definition supplies an SDK. */
interface CrashReporter {
    fun record(throwable: Throwable)
}

/** Event names shared features emit. */
object AnalyticsEvents {
    const val SCREEN_VIEW = "screen_view"
    const val LOGIN = "login"
    const val SIGN_UP = "sign_up"
    const val LOGOUT = "logout"
    const val SEARCH = "search"
    const val VIEW_ITEM = "view_item"
}
