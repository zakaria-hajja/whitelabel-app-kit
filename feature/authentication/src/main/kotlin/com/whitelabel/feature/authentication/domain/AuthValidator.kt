package com.whitelabel.feature.authentication.domain

internal enum class EmailError { BLANK, INVALID }

private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

internal object AuthValidator {
    /** Checks the trimmed email; the server has the final word. */
    fun email(raw: String): EmailError? {
        val email = raw.trim()
        return when {
            email.isEmpty() -> EmailError.BLANK
            !EMAIL.matches(email) -> EmailError.INVALID
            else -> null
        }
    }
}
