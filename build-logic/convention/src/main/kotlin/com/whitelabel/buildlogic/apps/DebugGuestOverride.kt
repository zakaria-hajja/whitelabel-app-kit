package com.whitelabel.buildlogic.apps

import java.util.Properties

private const val EMAIL_KEY = "debug.guest.email"
private const val PASSWORD_KEY = "debug.guest.password"

/** The account `local.properties` names for Debug's "Continue as guest (debug)", instead of the seeded alice. */
data class DebugGuestOverride(val email: String, val password: String)

/**
 * Reads `debug.guest.email` and `debug.guest.password` (trimmed; blank counts as absent) from
 * [properties], the build's `local.properties` or null when there is none. Both or neither: one alone
 * fails, naming [label].
 */
fun debugGuestOverride(properties: Properties?, label: String): DebugGuestOverride? {
    fun value(key: String) = properties?.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }
    val email = value(EMAIL_KEY)
    val password = value(PASSWORD_KEY)
    return when {
        email != null && password != null -> DebugGuestOverride(email, password)
        email == null && password == null -> null
        else -> throw InvalidAppConfigException(listOf("$label: $EMAIL_KEY and $PASSWORD_KEY must be set together"))
    }
}

/**
 * The string resources that replace the shell's Debug defaults (`foundation_debug_guest_*`), escaped.
 * Debug variants only, so Release never contains an account.
 */
fun debugGuestResValues(override: DebugGuestOverride?, buildType: String): Map<String, String> =
    if (override == null || buildType != "debug") {
        emptyMap()
    } else {
        mapOf(
            "foundation_debug_guest_email" to androidStringResource(override.email),
            "foundation_debug_guest_password" to androidStringResource(override.password),
        )
    }
