package com.whitelabel.core.data.user

import com.whitelabel.core.domain.User
import kotlinx.serialization.Serializable

/** `UserDto` from the backend contract (fields the app doesn't use are ignored). */
@Serializable
internal data class UserDto(
    val id: String,
    val email: String,
    val firstName: String? = null,
    val lastName: String? = null,
)

internal fun UserDto.toUser() = User(id = id, email = email, firstName = firstName, lastName = lastName)
