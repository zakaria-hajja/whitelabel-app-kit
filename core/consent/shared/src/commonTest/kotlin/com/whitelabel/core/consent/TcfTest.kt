package com.whitelabel.core.consent

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test

class TcfTest {
    @Test fun `missing or purpose 1 denied is not allowed`() {
        assertThat(storageAllowed(null)).isFalse()
        assertThat(storageAllowed("")).isFalse()
        assertThat(storageAllowed("0")).isFalse()
    }

    @Test fun `purpose 1 granted is allowed`() {
        assertThat(storageAllowed("1")).isTrue()
        assertThat(storageAllowed("10")).isTrue()
    }
}
