package com.whitelabel.core.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test

class ValidationTest {

    @Test fun `password length is checked at both boundaries`() {
        assertThat(PasswordPolicy.check("a".repeat(9))).isEqualTo(PasswordError.TOO_SHORT)
        assertThat(PasswordPolicy.check("a".repeat(10))).isNull()
        assertThat(PasswordPolicy.check("a".repeat(128))).isNull()
        assertThat(PasswordPolicy.check("a".repeat(129))).isEqualTo(PasswordError.TOO_LONG)
    }

    @Test fun `password is never trimmed`() {
        assertThat(PasswordPolicy.check(" ".repeat(10))).isNull()
        assertThat(PasswordPolicy.check("  abcdefg  ")).isNull() // 11 chars with the spaces
    }

    @Test fun `name length counts utf-16 units like the backend`() {
        val emoji = "😀" // one emoji, two UTF-16 units
        assertThat(NamePolicy.check(emoji.repeat(25))).isNull()
        assertThat(NamePolicy.check(emoji.repeat(26))).isEqualTo(NameError.TOO_LONG)
    }

    @Test fun `name is trimmed and blank is allowed`() {
        assertThat(NamePolicy.check("   ")).isNull()
        assertThat(NamePolicy.check("  " + "a".repeat(50) + "  ")).isNull()
        assertThat(NamePolicy.check("a".repeat(51))).isEqualTo(NameError.TOO_LONG)
    }
}
