package com.whitelabel.core.ui

import com.whitelabel.core.common.ApiErrorCode
import com.whitelabel.core.common.DataError

/**
 * A generic message for [this] error. Features map the codes they handle specially (e.g.
 * `INVALID_CREDENTIALS`) first and fall back to this.
 */
fun DataError.toUiText(): UiText = when (this) {
    DataError.Network.NO_INTERNET -> UiText.Resource(R.string.core_ui_error_no_internet)
    DataError.Network.REQUEST_TIMEOUT -> UiText.Resource(R.string.core_ui_error_timeout)
    DataError.Network.SERIALIZATION, DataError.Network.UNKNOWN -> UiText.Resource(R.string.core_ui_error_unknown)
    is DataError.Local -> UiText.Resource(R.string.core_ui_error_storage)
    is DataError.Api -> toUiText()
}

private fun DataError.Api.toUiText(): UiText = when (code) {
    ApiErrorCode.RATE_LIMITED -> retryAfterSeconds
        ?.let { UiText.Resource(R.string.core_ui_error_rate_limited_seconds, listOf(it)) }
        ?: UiText.Resource(R.string.core_ui_error_rate_limited)
    ApiErrorCode.UNAUTHENTICATED,
    ApiErrorCode.TOKEN_EXPIRED,
    ApiErrorCode.INVALID_REFRESH_TOKEN,
    -> UiText.Resource(R.string.core_ui_error_session_expired)
    ApiErrorCode.INTERNAL_ERROR -> UiText.Resource(R.string.core_ui_error_server)
    ApiErrorCode.TENANT_SUSPENDED -> UiText.Resource(R.string.core_ui_error_suspended)
    ApiErrorCode.FEATURE_DISABLED -> UiText.Resource(R.string.core_ui_error_unavailable)
    ApiErrorCode.NOT_FOUND -> UiText.Resource(R.string.core_ui_error_not_found)
    else -> UiText.Resource(R.string.core_ui_error_unknown)
}
