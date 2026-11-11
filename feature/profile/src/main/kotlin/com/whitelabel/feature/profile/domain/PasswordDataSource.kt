package com.whitelabel.feature.profile.domain

import com.whitelabel.core.common.DataError
import com.whitelabel.core.common.EmptyResult

/** `PATCH users/me/password`. On success the backend signs out the user's other sessions; this one stays. */
internal interface PasswordDataSource {
    suspend fun change(current: String, new: String): EmptyResult<DataError>
}
