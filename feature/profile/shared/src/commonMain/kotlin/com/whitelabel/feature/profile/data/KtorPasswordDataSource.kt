package com.whitelabel.feature.profile.data

import com.whitelabel.core.common.DataError
import com.whitelabel.core.common.EmptyResult
import com.whitelabel.core.common.asEmptyResult
import com.whitelabel.core.network.patchResult
import com.whitelabel.feature.profile.domain.PasswordDataSource
import io.ktor.client.HttpClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
private data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)

internal class KtorPasswordDataSource(
    private val client: HttpClient,
) : PasswordDataSource {

    override suspend fun change(current: String, new: String): EmptyResult<DataError> =
        client.patchResult<ChangePasswordRequest, JsonObject>("users/me/password", ChangePasswordRequest(current, new))
            .asEmptyResult()
}
