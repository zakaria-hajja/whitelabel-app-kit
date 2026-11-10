package com.whitelabel.core.ui

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.whitelabel.core.common.ApiErrorCode
import com.whitelabel.core.common.DataError
import org.junit.jupiter.api.Test

class DataErrorUiTextTest {

    @Test fun `no internet maps to the offline message`() {
        assertThat(DataError.Network.NO_INTERNET.toUiText())
            .isEqualTo(UiText.Resource(R.string.core_ui_error_no_internet))
    }

    @Test fun `rate limited with retry after names the seconds`() {
        val error = DataError.Api(ApiErrorCode.RATE_LIMITED, 429, retryAfterSeconds = 30)
        assertThat(error.toUiText())
            .isEqualTo(UiText.Resource(R.string.core_ui_error_rate_limited_seconds, listOf(30L)))
    }

    @Test fun `rate limited without retry after uses the plain message`() {
        assertThat(DataError.Api(ApiErrorCode.RATE_LIMITED, 429).toUiText())
            .isEqualTo(UiText.Resource(R.string.core_ui_error_rate_limited))
    }

    @Test fun `session errors map to session expired`() {
        listOf(ApiErrorCode.UNAUTHENTICATED, ApiErrorCode.TOKEN_EXPIRED, ApiErrorCode.INVALID_REFRESH_TOKEN).forEach {
            assertThat(DataError.Api(it, 401).toUiText())
                .isEqualTo(UiText.Resource(R.string.core_ui_error_session_expired))
        }
    }

    @Test fun `every api code maps to a message`() {
        ApiErrorCode.entries.forEach {
            assertThat(DataError.Api(it, 400).toUiText()).isInstanceOf(UiText.Resource::class)
        }
    }

    @Test fun `other errors map to their messages`() {
        val expected = mapOf(
            DataError.Network.REQUEST_TIMEOUT to R.string.core_ui_error_timeout,
            DataError.Network.SERIALIZATION to R.string.core_ui_error_unknown,
            DataError.Api(ApiErrorCode.INTERNAL_ERROR, 500) to R.string.core_ui_error_server,
            DataError.Api(ApiErrorCode.TENANT_SUSPENDED, 403) to R.string.core_ui_error_suspended,
            DataError.Api(ApiErrorCode.FEATURE_DISABLED, 404) to R.string.core_ui_error_unavailable,
            DataError.Api(ApiErrorCode.NOT_FOUND, 404) to R.string.core_ui_error_not_found,
            DataError.Local.DISK_FULL to R.string.core_ui_error_storage,
            DataError.Api(ApiErrorCode.VALIDATION_ERROR, 400) to R.string.core_ui_error_unknown,
        )
        expected.forEach { (error, id) -> assertThat(error.toUiText()).isEqualTo(UiText.Resource(id)) }
    }
}
