package com.whitelabel.feature.authentication.domain

import com.whitelabel.core.common.DataError
import com.whitelabel.core.common.EmptyResult

internal interface PasswordResetDataSource {
    /** Asks for a reset email. The backend answers the same whether or not the account exists. */
    suspend fun requestReset(email: String): EmptyResult<DataError>

    suspend fun reset(token: String, newPassword: String): EmptyResult<DataError>
}
