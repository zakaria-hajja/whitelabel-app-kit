package com.whitelabel.feature.authentication.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test

class AuthValidatorTest {

    @Test fun `email is trimmed before checking`() {
        assertThat(AuthValidator.email("  a@b.co  ")).isNull()
    }

    @Test fun `blank email is blank`() {
        assertThat(AuthValidator.email("   ")).isEqualTo(EmailError.BLANK)
    }

    @Test fun `email without a dotted domain is invalid`() {
        listOf("a@b", "ab.co", "a @b.co", "@b.co").forEach {
            assertThat(AuthValidator.email(it)).isEqualTo(EmailError.INVALID)
        }
    }
}
