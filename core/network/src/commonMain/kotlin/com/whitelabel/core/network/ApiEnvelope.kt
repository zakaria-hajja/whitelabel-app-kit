package com.whitelabel.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Lenient JSON shared by the client's ContentNegotiation and envelope decoding. */
@PublishedApi
internal val networkJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

@PublishedApi
@Serializable
internal data class ApiEnvelope<T>(
    val data: T? = null,
    val meta: ApiMeta? = null,
)

@PublishedApi
@Serializable
internal data class ApiMeta(
    val requestId: String? = null,
    val pagination: PaginationMeta? = null,
)

@PublishedApi
@Serializable
internal data class PaginationMeta(
    val page: Int,
    val pageSize: Int,
    val totalItems: Int,
    val totalPages: Int,
)

@PublishedApi
@Serializable
internal data class ApiErrorEnvelope(val error: ApiErrorBody? = null)

@PublishedApi
@Serializable
internal data class ApiErrorBody(
    val code: String? = null,
    val message: String? = null,
    val details: List<ApiErrorDetail>? = null,
)

@PublishedApi
@Serializable
internal data class ApiErrorDetail(val path: String, val message: String)
