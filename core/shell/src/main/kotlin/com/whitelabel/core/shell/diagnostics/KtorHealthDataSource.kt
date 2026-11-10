package com.whitelabel.core.shell.diagnostics

import com.whitelabel.core.common.DataError
import com.whitelabel.core.common.Result
import com.whitelabel.core.common.map
import com.whitelabel.core.network.getResult
import io.ktor.client.HttpClient
import kotlinx.serialization.Serializable
import javax.inject.Inject

class KtorHealthDataSource @Inject constructor(
    private val httpClient: HttpClient,
) : HealthDataSource {
    override suspend fun check(): Result<HealthStatus, DataError> =
        httpClient.getResult<HealthDto>("health").map { HealthStatus(status = it.status, databaseUp = it.db == "up") }
}

@Serializable
private data class HealthDto(val status: String, val db: String)
