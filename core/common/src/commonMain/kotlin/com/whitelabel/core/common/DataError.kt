package com.whitelabel.core.common

data class FieldError(val path: String, val message: String)

sealed interface DataError : Error {
    enum class Network : DataError {
        NO_INTERNET,
        REQUEST_TIMEOUT,
        SERIALIZATION,
        UNKNOWN,
    }

    data class Api(
        val code: ApiErrorCode,
        val httpStatus: Int,
        val fieldErrors: List<FieldError> = emptyList(),
        val retryAfterSeconds: Long? = null,
    ) : DataError

    enum class Local : DataError {
        DISK_FULL,
        NOT_FOUND,
        UNKNOWN,
    }
}
