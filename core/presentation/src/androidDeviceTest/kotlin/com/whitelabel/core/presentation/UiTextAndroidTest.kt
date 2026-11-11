package com.whitelabel.core.presentation

import com.whitelabel.core.presentation.resources.Res
import com.whitelabel.core.presentation.resources.core_ui_error_no_internet
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

/** Shared strings resolve on Android too, in the device's language. */
class UiTextAndroidTest {

    private val original = Locale.getDefault()

    @AfterTest fun restoreLocale() = Locale.setDefault(original)

    @Test fun resolvesFrenchOnAndroid() = runTest {
        Locale.setDefault(Locale.FRENCH)
        assertEquals(
            "Pas de connexion Internet. Vérifiez votre connexion et réessayez.",
            UiText.Resource(Res.string.core_ui_error_no_internet).asStringAsync(),
        )
    }
}
