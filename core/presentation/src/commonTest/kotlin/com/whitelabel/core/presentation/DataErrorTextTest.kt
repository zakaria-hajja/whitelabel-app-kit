package com.whitelabel.core.presentation

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.whitelabel.core.common.ApiErrorCode
import com.whitelabel.core.common.DataError
import com.whitelabel.core.presentation.resources.Res
import com.whitelabel.core.presentation.resources.core_ui_error_no_internet
import com.whitelabel.core.presentation.resources.core_ui_error_not_found
import com.whitelabel.core.presentation.resources.core_ui_error_rate_limited
import com.whitelabel.core.presentation.resources.core_ui_error_rate_limited_seconds
import com.whitelabel.core.presentation.resources.core_ui_error_server
import com.whitelabel.core.presentation.resources.core_ui_error_session_expired
import com.whitelabel.core.presentation.resources.core_ui_error_storage
import com.whitelabel.core.presentation.resources.core_ui_error_suspended
import com.whitelabel.core.presentation.resources.core_ui_error_timeout
import com.whitelabel.core.presentation.resources.core_ui_error_unavailable
import com.whitelabel.core.presentation.resources.core_ui_error_unknown
import kotlin.test.Test

class DataErrorTextTest {

    @Test fun `no internet maps to the offline message`() {
        assertThat(DataError.Network.NO_INTERNET.toUiText())
            .isEqualTo(UiText.Resource(Res.string.core_ui_error_no_internet))
    }

    @Test fun `rate limited with retry after names the seconds`() {
        val error = DataError.Api(ApiErrorCode.RATE_LIMITED, 429, retryAfterSeconds = 30)
        assertThat(error.toUiText())
            .isEqualTo(UiText.Resource(Res.string.core_ui_error_rate_limited_seconds, listOf(30L)))
    }

    @Test fun `rate limited without retry after uses the plain message`() {
        assertThat(DataError.Api(ApiErrorCode.RATE_LIMITED, 429).toUiText())
            .isEqualTo(UiText.Resource(Res.string.core_ui_error_rate_limited))
    }

    @Test fun `session errors map to session expired`() {
        listOf(ApiErrorCode.UNAUTHENTICATED, ApiErrorCode.TOKEN_EXPIRED, ApiErrorCode.INVALID_REFRESH_TOKEN).forEach {
            assertThat(DataError.Api(it, 401).toUiText())
                .isEqualTo(UiText.Resource(Res.string.core_ui_error_session_expired))
        }
    }

    @Test fun `every api code maps to a message`() {
        ApiErrorCode.entries.forEach {
            assertThat(DataError.Api(it, 400).toUiText()).isInstanceOf(UiText.Resource::class)
        }
    }

    @Test fun `other errors map to their messages`() {
        val expected = mapOf(
            DataError.Network.REQUEST_TIMEOUT to Res.string.core_ui_error_timeout,
            DataError.Network.SERIALIZATION to Res.string.core_ui_error_unknown,
            DataError.Api(ApiErrorCode.INTERNAL_ERROR, 500) to Res.string.core_ui_error_server,
            DataError.Api(ApiErrorCode.TENANT_SUSPENDED, 403) to Res.string.core_ui_error_suspended,
            DataError.Api(ApiErrorCode.FEATURE_DISABLED, 404) to Res.string.core_ui_error_unavailable,
            DataError.Api(ApiErrorCode.NOT_FOUND, 404) to Res.string.core_ui_error_not_found,
            DataError.Local.DISK_FULL to Res.string.core_ui_error_storage,
            DataError.Api(ApiErrorCode.VALIDATION_ERROR, 400) to Res.string.core_ui_error_unknown,
        )
        expected.forEach { (error, id) -> assertThat(error.toUiText()).isEqualTo(UiText.Resource(id)) }
    }
}
