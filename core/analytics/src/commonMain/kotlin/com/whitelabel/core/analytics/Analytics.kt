package com.whitelabel.core.analytics

private val EVENT_NAME = Regex("^[a-z][a-z0-9_]{0,39}$")
private val USER_PROPERTY_NAME = Regex("^[a-z][a-z0-9_]{0,23}$")
private val RESERVED_USER_PROPERTY_PREFIXES = listOf("firebase_", "google_", "ga_")
private const val USER_PROPERTY_VALUE_MAX = 36

/**
 * One analytics event. [name] is lowercase snake case, at most 40 characters (the strictest common
 * SDK limit). [params] never carry personal data: no email, name, token or free text the user typed.
 */
data class AnalyticsEvent(val name: String, val params: Map<String, String> = emptyMap()) {
    init {
        require(EVENT_NAME.matches(name)) { "Analytics event name must match ${EVENT_NAME.pattern}: '$name'" }
    }
}

/**
 * One user property: an attribute of the user that later events are grouped by. [name] is lowercase
 * snake case, at most 24 characters, without Firebase's reserved prefixes (`firebase_`, `google_`,
 * `ga_`). [value] is 1 to 36 characters (UTF-16 units, so an emoji counts as two). Firebase's limits.
 * A user property **never carries health data** or personal data: only coarse buckets of app usage
 * (`garden_size_bucket=3_5`), never a date, a symptom, a cycle detail or anything the user typed.
 */
data class UserProperty(val name: String, val value: String) {
    init {
        require(USER_PROPERTY_NAME.matches(name)) { "User property name must match ${USER_PROPERTY_NAME.pattern}: '$name'" }
        require(RESERVED_USER_PROPERTY_PREFIXES.none { name.startsWith(it) }) {
            "User property name must not start with ${RESERVED_USER_PROPERTY_PREFIXES.joinToString(", ")}: '$name'"
        }
        require(value.length in 1..USER_PROPERTY_VALUE_MAX) {
            "User property value must be 1 to $USER_PROPERTY_VALUE_MAX characters: '$name' has ${value.length}"
        }
    }
}

/** Where events go. The shell provides a logging one unless the app's definition supplies an SDK. */
interface Analytics {
    fun track(event: AnalyticsEvent)

    /**
     * Sets [property] on the user, under the same consent rules as [track]. Never health data (see
     * [UserProperty]). The default does nothing, so an existing implementation keeps compiling;
     * every foundation implementation overrides it.
     */
    fun setUserProperty(property: UserProperty) {}
}

/** Sets the user property [name] to [value]; throws [IllegalArgumentException] for a name or value outside [UserProperty]'s limits. */
fun Analytics.setUserProperty(name: String, value: String) = setUserProperty(UserProperty(name, value))

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
