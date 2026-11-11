package com.whitelabel.buildlogic

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test

class MockApiDependencyTest {

    @Test fun `mock api is off by default`() {
        assertThat(mockApiDependency(emptyMap())).isNull()
    }

    @Test fun `mock api true adds a debug-only dependency`() {
        assertThat(mockApiDependency(mapOf("whitelabel.mockApi" to "true"))).isEqualTo("debugImplementation" to ":core:mockapi")
    }

    @Test fun `any other value is off`() {
        listOf("yes", "TRUE ", "false", "", null).forEach { value ->
            assertThat(mockApiDependency(mapOf("whitelabel.mockApi" to value))).isNull()
        }
    }
}
