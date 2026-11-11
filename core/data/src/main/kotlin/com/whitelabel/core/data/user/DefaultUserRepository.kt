package com.whitelabel.core.data.user

import com.whitelabel.core.common.DataError
import com.whitelabel.core.common.EmptyResult
import com.whitelabel.core.common.asEmptyResult
import com.whitelabel.core.common.onSuccess
import com.whitelabel.core.domain.User
import com.whitelabel.core.domain.UserRepository
import com.whitelabel.core.network.getResult
import com.whitelabel.core.network.patchResult
import io.ktor.client.HttpClient
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class DefaultUserRepository @Inject constructor(
    private val client: HttpClient,
    private val cache: UserCache,
) : UserRepository {

    override val user: Flow<User?> = cache.user

    override suspend fun refresh(): EmptyResult<DataError> =
        client.getResult<UserDto>("users/me").onSuccess { cache.write(it) }.asEmptyResult()

    override suspend fun update(firstName: String?, lastName: String?): EmptyResult<DataError> {
        // A JsonObject, because the client drops null properties and a cleared name must send null.
        val body = buildJsonObject {
            put("firstName", firstName)
            put("lastName", lastName)
        }
        return client.patchResult<JsonObject, UserDto>("users/me", body).onSuccess { cache.write(it) }.asEmptyResult()
    }
}
