package com.whitelabel.core.network

import com.whitelabel.core.common.DataError
import com.whitelabel.core.common.Result
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

// Paths are relative to the versioned base URL. A leading slash would resolve against the
// host root and drop `/api/v1`, so it is stripped.
@PublishedApi
internal fun relativePath(path: String): String = path.trimStart('/')

@PublishedApi
internal fun HttpRequestBuilder.applyQuery(query: Map<String, Any?>) {
    query.forEach { (key, value) -> if (value != null) parameter(key, value) }
}

suspend inline fun <reified T> HttpClient.getResult(
    path: String,
    query: Map<String, Any?> = emptyMap(),
): Result<T, DataError> = safeCall {
    get(relativePath(path)) { applyQuery(query) }.toResult<T>()
}

suspend inline fun <reified T> HttpClient.getPageResult(
    path: String,
    query: Map<String, Any?> = emptyMap(),
): Result<Page<T>, DataError> = safeCall {
    get(relativePath(path)) { applyQuery(query) }.toPageResult<T>()
}

suspend inline fun <reified B, reified T> HttpClient.postResult(path: String, body: B): Result<T, DataError> =
    safeCall {
        post(relativePath(path)) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.toResult<T>()
    }

suspend inline fun <reified B, reified T> HttpClient.patchResult(path: String, body: B): Result<T, DataError> =
    safeCall {
        patch(relativePath(path)) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.toResult<T>()
    }

suspend inline fun <reified T> HttpClient.deleteResult(path: String): Result<T, DataError> =
    safeCall { delete(relativePath(path)).toResult<T>() }
