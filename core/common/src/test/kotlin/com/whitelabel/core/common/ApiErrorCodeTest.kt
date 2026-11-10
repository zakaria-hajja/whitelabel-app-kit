package com.whitelabel.core.common

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class ApiErrorCodeTest {

    @Test
    fun `every backend code round-trips by exact name`() {
        ApiErrorCode.entries.filter { it != ApiErrorCode.UNKNOWN }
            .forEach { assertThat(ApiErrorCode.fromWire(it.name)).isEqualTo(it) }
    }

    @Test
    fun `unknown, null, blank and lowercase codes map to UNKNOWN`() {
        listOf("NEW_CODE", null, "", "not_found", "UNKNOWN").forEach {
            assertThat(ApiErrorCode.fromWire(it)).isEqualTo(ApiErrorCode.UNKNOWN)
        }
    }

    @Test
    fun `fromStatus maps by status`() {
        mapOf(
            401 to ApiErrorCode.UNAUTHENTICATED,
            403 to ApiErrorCode.FORBIDDEN,
            404 to ApiErrorCode.NOT_FOUND,
            413 to ApiErrorCode.PAYLOAD_TOO_LARGE,
            429 to ApiErrorCode.RATE_LIMITED,
            500 to ApiErrorCode.INTERNAL_ERROR,
            599 to ApiErrorCode.INTERNAL_ERROR,
            499 to ApiErrorCode.UNKNOWN,
            600 to ApiErrorCode.UNKNOWN,
            400 to ApiErrorCode.UNKNOWN,
            200 to ApiErrorCode.UNKNOWN,
        ).forEach { (status, code) ->
            assertThat(ApiErrorCode.fromStatus(status)).isEqualTo(code)
        }
    }
}
