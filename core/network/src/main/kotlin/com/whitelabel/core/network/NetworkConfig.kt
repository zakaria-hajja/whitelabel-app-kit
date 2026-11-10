package com.whitelabel.core.network

const val CLIENT_ID_HEADER = "X-Client-Id"

/**
 * Everything the HTTP client needs from the host app. [baseUrl] is the versioned API root
 * (e.g. `https://host/api/v1/`). [logger] receives log lines (headers only, `Authorization`
 * redacted); `null` disables logging.
 */
data class NetworkConfig(
    val baseUrl: String,
    val clientSlug: String,
    val userAgent: String,
    val logger: ((String) -> Unit)? = null,
)
