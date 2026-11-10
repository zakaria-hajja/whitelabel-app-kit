package com.whitelabel.feature.authentication.data

import com.whitelabel.core.common.DataError
import com.whitelabel.core.common.EmptyResult
import com.whitelabel.core.common.asEmptyResult
import com.whitelabel.core.network.postPublicResult
import com.whitelabel.feature.authentication.domain.PasswordResetDataSource
import io.ktor.client.HttpClient
import javax.inject.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
private data class ForgotPasswordRequest(val email: String)

@Serializable
private data class ResetPasswordRequest(val token: String, val newPassword: String)

internal class KtorPasswordResetDataSource @Inject constructor(
    private val client: HttpClient,
) : PasswordResetDataSource {

    override suspend fun requestReset(email: String): EmptyResult<DataError> =
        client.postPublicResult<ForgotPasswordRequest, JsonObject>("auth/forgot-password", ForgotPasswordRequest(email.trim()))
            .asEmptyResult()

    override suspend fun reset(token: String, newPassword: String): EmptyResult<DataError> =
        client.postPublicResult<ResetPasswordRequest, JsonObject>("auth/reset-password", ResetPasswordRequest(token, newPassword))
            .asEmptyResult()
}
