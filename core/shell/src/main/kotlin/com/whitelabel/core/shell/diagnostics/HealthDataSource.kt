package com.whitelabel.core.shell.diagnostics

import com.whitelabel.core.common.DataError
import com.whitelabel.core.common.Result

/** Backend reachability, for the temporary brand preview screen. */
internal interface HealthDataSource {
    suspend fun check(): Result<HealthStatus, DataError>
}

internal data class HealthStatus(val status: String, val databaseUp: Boolean)
