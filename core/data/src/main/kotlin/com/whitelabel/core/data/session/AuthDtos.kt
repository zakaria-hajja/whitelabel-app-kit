package com.whitelabel.core.data.session

import com.whitelabel.core.data.user.UserDto
import kotlinx.serialization.Serializable

@Serializable
internal data class LoginRequest(val email: String, val password: String)

/** Null names are omitted from the body (the client drops nulls); the register schema doesn't accept null. */
@Serializable
internal data class RegisterRequest(
    val email: String,
    val password: String,
    val firstName: String? = null,
    val lastName: String? = null,
)

@Serializable
internal data class LogoutRequest(val refreshToken: String)

@Serializable
internal data class TokenPairDto(val accessToken: String, val refreshToken: String)

@Serializable
internal data class AuthResultDto(val user: UserDto, val tokens: TokenPairDto)
