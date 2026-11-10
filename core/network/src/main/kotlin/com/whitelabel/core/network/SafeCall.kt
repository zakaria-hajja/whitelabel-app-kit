package com.whitelabel.core.network

import com.whitelabel.core.common.ApiErrorCode
import com.whitelabel.core.common.DataError
import com.whitelabel.core.common.FieldError
import com.whitelabel.core.common.Result
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.JsonConvertException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import java.io.IOException
import java.nio.channels.UnresolvedAddressException
import kotlin.coroutines.cancellation.CancellationException

/** Runs [block], mapping every thrown failure to a [DataError]. Cancellation is rethrown. */
@PublishedApi
internal suspend inline fun <T> safeCall(block: () -> Result<T, DataError>): Result<T, DataError> =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.Error(e.toDataError())
    }

@PublishedApi
internal fun Exception.toDataError(): DataError = when (this) {
    is HttpRequestTimeoutException,
    is SocketTimeoutException,
    is ConnectTimeoutException,
    is java.net.SocketTimeoutException,
    -> DataError.Network.REQUEST_TIMEOUT
    is IOException, is UnresolvedAddressException -> DataError.Network.NO_INTERNET
    is SerializationException, is JsonConvertException -> DataError.Network.SERIALIZATION
    else -> DataError.Network.UNKNOWN
}

/** Unwraps a success envelope into its `data`, or maps the failure response. */
@PublishedApi
internal suspend inline fun <reified T> HttpResponse.toResult(): Result<T, DataError> {
    if (!status.isSuccess()) return Result.Error(toApiError())
    val data = networkJson.decodeFromString<ApiEnvelope<T>>(bodyAsText()).data
        ?: return Result.Error(DataError.Network.SERIALIZATION)
    return Result.Success(data)
}

@PublishedApi
internal suspend inline fun <reified T> HttpResponse.toPageResult(): Result<Page<T>, DataError> {
    if (!status.isSuccess()) return Result.Error(toApiError())
    val envelope = networkJson.decodeFromString<ApiEnvelope<List<T>>>(bodyAsText())
    val items = envelope.data ?: return Result.Error(DataError.Network.SERIALIZATION)
    val p = envelope.meta?.pagination ?: return Result.Error(DataError.Network.SERIALIZATION)
    return Result.Success(Page(items, p.page, p.pageSize, p.totalItems, p.totalPages))
}

/** Maps a non-success response. A body that is not an error envelope falls back to the status. */
@PublishedApi
internal suspend fun HttpResponse.toApiError(): DataError.Api {
    val httpStatus = status.value
    val body = try {
        networkJson.decodeFromString<ApiErrorEnvelope>(bodyAsText()).error
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } ?: return DataError.Api(ApiErrorCode.fromStatus(httpStatus), httpStatus)
    return DataError.Api(
        code = ApiErrorCode.fromWire(body.code),
        httpStatus = httpStatus,
        fieldErrors = body.details.orEmpty().map { FieldError(it.path, it.message) },
        retryAfterSeconds = headers[HttpHeaders.RetryAfter]?.trim()?.toLongOrNull(),
    )
}
