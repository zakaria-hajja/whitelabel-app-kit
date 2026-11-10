package com.whitelabel.feature.authentication.presentation

import com.whitelabel.core.common.ApiErrorCode
import com.whitelabel.core.common.DataError
import com.whitelabel.core.domain.NameError
import com.whitelabel.core.domain.NamePolicy
import com.whitelabel.core.domain.PasswordError
import com.whitelabel.core.domain.PasswordPolicy
import com.whitelabel.core.ui.UiText
import com.whitelabel.core.ui.toUiText
import com.whitelabel.feature.authentication.R
import com.whitelabel.feature.authentication.domain.EmailError

internal fun EmailError.toUiText(): UiText = when (this) {
    EmailError.BLANK -> UiText.Resource(R.string.auth_error_email_blank)
    EmailError.INVALID -> UiText.Resource(R.string.auth_error_email_invalid)
}

internal fun PasswordError.toUiText(): UiText = when (this) {
    PasswordError.TOO_SHORT -> UiText.Resource(R.string.auth_error_password_short, listOf(PasswordPolicy.MIN))
    PasswordError.TOO_LONG -> UiText.Resource(R.string.auth_error_password_long, listOf(PasswordPolicy.MAX))
}

internal fun NameError.toUiText(): UiText = UiText.Resource(R.string.auth_error_name_long, listOf(NamePolicy.MAX))

/** Auth screens' wording for errors, falling back to the shared messages. */
internal fun DataError.toAuthUiText(): UiText =
    if (this is DataError.Api && code == ApiErrorCode.INVALID_CREDENTIALS) {
        UiText.Resource(R.string.auth_error_invalid_credentials)
    } else {
        toUiText()
    }
