package com.whitelabel.core.data.user

import com.whitelabel.core.common.DataError
import com.whitelabel.core.common.EmptyResult
import com.whitelabel.core.common.asEmptyResult
import com.whitelabel.core.common.onSuccess
import com.whitelabel.core.domain.User
import com.whitelabel.core.domain.UserRepository
import com.whitelabel.core.network.getResult
import io.ktor.client.HttpClient
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

internal class DefaultUserRepository @Inject constructor(
    private val client: HttpClient,
    private val cache: UserCache,
) : UserRepository {

    override val user: Flow<User?> = cache.user

    override suspend fun refresh(): EmptyResult<DataError> =
        client.getResult<UserDto>("users/me").onSuccess { cache.write(it) }.asEmptyResult()
}
