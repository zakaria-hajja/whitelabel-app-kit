package com.whitelabel.core.shell

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class ScreenNameTest {
    @Test fun `screen name is the route class without package or arguments`() {
        assertThat(screenName("com.example.feature.HomeRoute")).isEqualTo("HomeRoute")
        assertThat(screenName("com.example.feature.DetailRoute/{id}")).isEqualTo("DetailRoute")
        assertThat(screenName("com.example.feature.ResetRoute?token={token}")).isEqualTo("ResetRoute")
    }
}
