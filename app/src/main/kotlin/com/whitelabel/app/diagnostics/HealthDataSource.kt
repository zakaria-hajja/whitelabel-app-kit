package com.whitelabel.app.diagnostics

import com.whitelabel.core.common.DataError
import com.whitelabel.core.common.Result

/** Backend reachability, for the temporary brand preview screen. */
interface HealthDataSource {
    suspend fun check(): Result<HealthStatus, DataError>
}

data class HealthStatus(val status: String, val databaseUp: Boolean)
