package com.whitelabel.core.presentation

import com.whitelabel.core.common.ApiErrorCode
import com.whitelabel.core.common.DataError
import com.whitelabel.core.presentation.resources.Res
import com.whitelabel.core.presentation.resources.core_ui_error_no_internet
import com.whitelabel.core.presentation.resources.core_ui_error_not_found
import com.whitelabel.core.presentation.resources.core_ui_error_rate_limited
import com.whitelabel.core.presentation.resources.core_ui_error_rate_limited_seconds
import com.whitelabel.core.presentation.resources.core_ui_error_server
import com.whitelabel.core.presentation.resources.core_ui_error_session_expired
import com.whitelabel.core.presentation.resources.core_ui_error_storage
import com.whitelabel.core.presentation.resources.core_ui_error_suspended
import com.whitelabel.core.presentation.resources.core_ui_error_timeout
import com.whitelabel.core.presentation.resources.core_ui_error_unavailable
import com.whitelabel.core.presentation.resources.core_ui_error_unknown

/**
 * A generic message for [this] error. Features map the codes they handle specially (e.g.
 * `INVALID_CREDENTIALS`) first and fall back to this.
 */
fun DataError.toUiText(): UiText = when (this) {
    DataError.Network.NO_INTERNET -> UiText.Resource(Res.string.core_ui_error_no_internet)
    DataError.Network.REQUEST_TIMEOUT -> UiText.Resource(Res.string.core_ui_error_timeout)
    DataError.Network.SERIALIZATION, DataError.Network.UNKNOWN -> UiText.Resource(Res.string.core_ui_error_unknown)
    is DataError.Local -> UiText.Resource(Res.string.core_ui_error_storage)
    is DataError.Api -> toUiText()
}

private fun DataError.Api.toUiText(): UiText = when (code) {
    ApiErrorCode.RATE_LIMITED -> retryAfterSeconds
        ?.let { UiText.Resource(Res.string.core_ui_error_rate_limited_seconds, listOf(it)) }
        ?: UiText.Resource(Res.string.core_ui_error_rate_limited)
    ApiErrorCode.UNAUTHENTICATED,
    ApiErrorCode.TOKEN_EXPIRED,
    ApiErrorCode.INVALID_REFRESH_TOKEN,
    -> UiText.Resource(Res.string.core_ui_error_session_expired)
    ApiErrorCode.INTERNAL_ERROR -> UiText.Resource(Res.string.core_ui_error_server)
    ApiErrorCode.TENANT_SUSPENDED -> UiText.Resource(Res.string.core_ui_error_suspended)
    ApiErrorCode.FEATURE_DISABLED -> UiText.Resource(Res.string.core_ui_error_unavailable)
    ApiErrorCode.NOT_FOUND -> UiText.Resource(Res.string.core_ui_error_not_found)
    else -> UiText.Resource(Res.string.core_ui_error_unknown)
}
