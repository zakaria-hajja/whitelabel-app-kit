package com.whitelabel.core.shell

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class RecentsProtectionTest {

    @Test fun `API 33 and up turn the recents screenshot off`() {
        assertThat(recentsProtection(sdkInt = 33, on = true)).isEqualTo(RecentsProtection.NO_SCREENSHOT)
        assertThat(recentsProtection(sdkInt = 36, on = true)).isEqualTo(RecentsProtection.NO_SCREENSHOT)
    }

    @Test fun `below API 33 the window is secure`() {
        assertThat(recentsProtection(sdkInt = 32, on = true)).isEqualTo(RecentsProtection.FLAG_SECURE)
        assertThat(recentsProtection(sdkInt = 26, on = true)).isEqualTo(RecentsProtection.FLAG_SECURE)
    }

    @Test fun `off means no protection on any API`() {
        assertThat(recentsProtection(sdkInt = 36, on = false)).isEqualTo(RecentsProtection.NONE)
        assertThat(recentsProtection(sdkInt = 26, on = false)).isEqualTo(RecentsProtection.NONE)
    }
}
