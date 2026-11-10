package com.whitelabel.buildlogic.clients

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class AndroidStringResourceTest {
    @Test fun `plain text is unchanged`() {
        assertThat(androidStringResource("Acme Shop")).isEqualTo("Acme Shop")
    }

    @Test fun `apostrophe and quote are escaped`() {
        assertThat(androidStringResource("Joe's \"Place\"")).isEqualTo("Joe\\'s \\\"Place\\\"")
    }

    @Test fun `backslash is escaped first`() {
        assertThat(androidStringResource("a\\b'")).isEqualTo("a\\\\b\\'")
    }

    @Test fun `leading at and question mark are escaped`() {
        assertThat(androidStringResource("@home")).isEqualTo("\\@home")
        assertThat(androidStringResource("?what")).isEqualTo("\\?what")
        assertThat(androidStringResource("a@b?c")).isEqualTo("a@b?c")
    }

    @Test fun `newline and tab become escapes`() {
        assertThat(androidStringResource("a\nb\tc")).isEqualTo("a\\nb\\tc")
    }
}
