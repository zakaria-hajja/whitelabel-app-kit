package com.whitelabel.core.domain

enum class PasswordError { TOO_SHORT, TOO_LONG }

/** The backend's rule for a new password (register, reset, change). Never trimmed. */
object PasswordPolicy {
    const val MIN = 10
    const val MAX = 128

    fun check(raw: String): PasswordError? = when {
        raw.length < MIN -> PasswordError.TOO_SHORT
        raw.length > MAX -> PasswordError.TOO_LONG
        else -> null
    }
}

enum class NameError { TOO_LONG }

/**
 * The backend's rule for a first or last name: at most [MAX] after trimming, counted in UTF-16
 * units as the backend does. Blank is allowed and means no name.
 */
object NamePolicy {
    const val MAX = 50

    fun check(raw: String): NameError? = if (raw.trim().length > MAX) NameError.TOO_LONG else null
}
