package com.whitelabel.buildlogic.apps

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import java.util.Properties
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DebugGuestOverrideTest {

    private companion object {
        const val LABEL = "local.properties"
        const val MESSAGE = "local.properties: debug.guest.email and debug.guest.password must be set together"
    }

    private fun props(vararg pairs: Pair<String, String>) = Properties().apply { pairs.forEach { (k, v) -> setProperty(k, v) } }

    private fun assertFailsTogether(properties: Properties) {
        val error = assertThrows(InvalidAppConfigException::class.java) { debugGuestOverride(properties, LABEL) }
        assertThat(error.message.orEmpty()).contains(MESSAGE)
    }

    @Test fun `no file or no keys means no override`() {
        assertThat(debugGuestOverride(null, LABEL)).isNull()
        assertThat(debugGuestOverride(Properties(), LABEL)).isNull()
    }

    @Test fun `both keys give the override, trimmed`() {
        val override = debugGuestOverride(props("debug.guest.email" to " bob@shared.test ", "debug.guest.password" to "Password123!"), LABEL)
        assertThat(override).isEqualTo(DebugGuestOverride("bob@shared.test", "Password123!"))
    }

    @Test fun `one key alone fails naming both`() {
        assertFailsTogether(props("debug.guest.email" to "bob@shared.test"))
        assertFailsTogether(props("debug.guest.password" to "Password123!"))
    }

    @Test fun `a blank key counts as absent`() {
        assertFailsTogether(props("debug.guest.email" to "bob@shared.test", "debug.guest.password" to "   "))
        assertThat(debugGuestOverride(props("debug.guest.email" to " ", "debug.guest.password" to ""), LABEL)).isNull()
    }

    @Test fun `only debug variants get the resources`() {
        val override = DebugGuestOverride("bob@shared.test", "Password123!")
        assertThat(debugGuestResValues(override, "release")).isEmpty()
        assertThat(debugGuestResValues(null, "debug")).isEmpty()
        assertThat(debugGuestResValues(override, "debug").keys)
            .isEqualTo(setOf("foundation_debug_guest_email", "foundation_debug_guest_password"))
    }

    @Test fun `the password is escaped as a string resource`() {
        val raw = "a'b\"c\\d@%s"
        val values = debugGuestResValues(DebugGuestOverride("bob@shared.test", raw), "debug")
        assertThat(values["foundation_debug_guest_password"]).isEqualTo(androidStringResource(raw))
    }
}
