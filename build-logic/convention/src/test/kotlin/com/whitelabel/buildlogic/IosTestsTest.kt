package com.whitelabel.buildlogic

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test

class IosTestsTest {

    @Test fun `ios tests run by default`() {
        assertThat(iosTestsEnabled(emptyMap())).isTrue()
    }

    @Test fun `skipIosTests true turns them off`() {
        assertThat(iosTestsEnabled(mapOf("whitelabel.skipIosTests" to "true"))).isFalse()
    }

    @Test fun `any other value keeps them on`() {
        listOf("yes", "TRUE ", "false", "", null).forEach { value ->
            assertThat(iosTestsEnabled(mapOf("whitelabel.skipIosTests" to value))).isTrue()
        }
    }
}
