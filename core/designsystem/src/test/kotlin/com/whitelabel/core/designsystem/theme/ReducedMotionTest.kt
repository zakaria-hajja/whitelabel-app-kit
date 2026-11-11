package com.whitelabel.core.designsystem.theme

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test

class ReducedMotionTest {

    @Test fun `animator scale 0 means reduced motion`() {
        assertThat(isReducedMotion(0f)).isTrue()
    }

    @Test fun `any other scale keeps motion`() {
        listOf(1f, 0.5f, 10f).forEach { assertThat(isReducedMotion(it)).isFalse() }
    }
}
