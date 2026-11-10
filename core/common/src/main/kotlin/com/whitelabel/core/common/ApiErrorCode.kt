package com.whitelabel.core.common

/** Error codes returned by the backend API, plus [UNKNOWN] for anything unrecognised. */
enum class ApiErrorCode {
    VALIDATION_ERROR,
    INVALID_CLIENT,
    UNAUTHENTICATED,
    TOKEN_EXPIRED,
    INVALID_CREDENTIALS,
    INVALID_REFRESH_TOKEN,
    INVALID_RESET_TOKEN,
    FORBIDDEN,
    TENANT_SUSPENDED,
    TENANT_MISMATCH,
    NOT_FOUND,
    FEATURE_DISABLED,
    EMAIL_ALREADY_REGISTERED,
    PAYLOAD_TOO_LARGE,
    RATE_LIMITED,
    INTERNAL_ERROR,
    UNKNOWN;

    companion object {
        /** Exact, case-sensitive match on the wire code; anything else is [UNKNOWN]. */
        fun fromWire(code: String?): ApiErrorCode =
            entries.firstOrNull { it.name == code && it != UNKNOWN } ?: UNKNOWN

        /** Fallback when the response carries no usable code. */
        fun fromStatus(status: Int): ApiErrorCode = when (status) {
            401 -> UNAUTHENTICATED
            403 -> FORBIDDEN
            404 -> NOT_FOUND
            413 -> PAYLOAD_TOO_LARGE
            429 -> RATE_LIMITED
            in 500..599 -> INTERNAL_ERROR
            else -> UNKNOWN
        }
    }
}
