package com.whitelabel.core.presentation

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.whitelabel.core.presentation.resources.Res
import com.whitelabel.core.presentation.resources.core_ui_error_no_internet
import com.whitelabel.core.presentation.resources.core_ui_error_rate_limited_seconds
import com.whitelabel.core.presentation.resources.core_ui_error_storage
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

/**
 * Shared strings resolve on iOS, where Swift reads them through [asStringAsync]. Not in commonTest:
 * on the JVM target (host tests only) resolving needs desktop graphics (Skiko), which CI lacks.
 */
class UiTextIosTest {

    @Test fun `asStringAsync resolves the English text`() = runTest {
        assertThat(UiText.Resource(Res.string.core_ui_error_no_internet).asStringAsync())
            .isEqualTo("No internet connection. Check your connection and try again.")
    }

    @Test fun `asStringAsync fills in arguments`() = runTest {
        assertThat(UiText.Resource(Res.string.core_ui_error_rate_limited_seconds, listOf(30L)).asStringAsync())
            .isEqualTo("Too many attempts. Try again in 30 seconds.")
    }

    @Test fun `asStringAsync unescapes apostrophes written the Android way`() = runTest {
        assertThat(UiText.Resource(Res.string.core_ui_error_storage).asStringAsync())
            .isEqualTo("Couldn't save on this device. Free up some space and try again.")
    }

    @Test fun `a dynamic text is returned as is`() = runTest {
        assertThat(UiText.Dynamic("Hello").asStringAsync()).isEqualTo("Hello")
    }
}
