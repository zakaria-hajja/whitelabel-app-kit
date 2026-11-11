package com.whitelabel.feature.authentication.presentation

import com.whitelabel.feature.authentication.shared.resources.Res
import com.whitelabel.feature.authentication.shared.resources.auth_error_email_blank
import com.whitelabel.feature.authentication.shared.resources.auth_error_email_invalid
import com.whitelabel.feature.authentication.shared.resources.auth_error_invalid_credentials
import com.whitelabel.feature.authentication.shared.resources.auth_error_name_long
import com.whitelabel.feature.authentication.shared.resources.auth_error_password_long
import com.whitelabel.feature.authentication.shared.resources.auth_error_password_short
import com.whitelabel.core.common.ApiErrorCode
import com.whitelabel.core.common.DataError
import com.whitelabel.core.domain.NameError
import com.whitelabel.core.domain.NamePolicy
import com.whitelabel.core.domain.PasswordError
import com.whitelabel.core.domain.PasswordPolicy
import com.whitelabel.core.presentation.UiText
import com.whitelabel.core.presentation.toUiText
import com.whitelabel.feature.authentication.domain.EmailError

fun EmailError.toUiText(): UiText = when (this) {
    EmailError.BLANK -> UiText.Resource(Res.string.auth_error_email_blank)
    EmailError.INVALID -> UiText.Resource(Res.string.auth_error_email_invalid)
}

fun PasswordError.toUiText(): UiText = when (this) {
    PasswordError.TOO_SHORT -> UiText.Resource(Res.string.auth_error_password_short, listOf(PasswordPolicy.MIN))
    PasswordError.TOO_LONG -> UiText.Resource(Res.string.auth_error_password_long, listOf(PasswordPolicy.MAX))
}

fun NameError.toUiText(): UiText = UiText.Resource(Res.string.auth_error_name_long, listOf(NamePolicy.MAX))

/** Auth screens' wording for errors, falling back to the shared messages. */
fun DataError.toAuthUiText(): UiText =
    if (this is DataError.Api && code == ApiErrorCode.INVALID_CREDENTIALS) {
        UiText.Resource(Res.string.auth_error_invalid_credentials)
    } else {
        toUiText()
    }
