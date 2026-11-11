package com.whitelabel.apps.showcase.shared

import com.whitelabel.core.presentation.UiText
import com.whitelabel.core.presentation.asStringAsync
import com.whitelabel.core.presentation.resources.Res as CoreRes
import com.whitelabel.core.presentation.resources.core_ui_error_no_internet
import com.whitelabel.feature.home.shared.resources.Res as HomeRes
import com.whitelabel.feature.home.shared.resources.home_greeting_named
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

/** The shared modules' strings are packaged with Showcase's iOS binaries, so Swift can show them. */
class ShowcaseStringsIosTest {

    @Test fun `a feature string resolves`() = runTest {
        assertEquals("Hello, Alice", UiText.Resource(HomeRes.string.home_greeting_named, listOf("Alice")).asStringAsync())
    }

    @Test fun `a core string resolves`() = runTest {
        assertEquals(
            "No internet connection. Check your connection and try again.",
            UiText.Resource(CoreRes.string.core_ui_error_no_internet).asStringAsync(),
        )
    }
}
