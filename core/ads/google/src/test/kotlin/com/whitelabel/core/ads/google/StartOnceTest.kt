package com.whitelabel.core.ads.google

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class StartOnceTest {

    @Test fun `the sdk starts once for every banner`() = runTest {
        val start = StartOnce()
        var calls = 0
        val begin: (() -> Unit) -> Unit = { done -> calls++; done() }

        assertThat(start.await(begin)).isEqualTo(true)
        assertThat(start.await(begin)).isEqualTo(true)
        assertThat(calls).isEqualTo(1)
        assertThat(start.isDone).isEqualTo(true)
    }

    @Test fun `a start that throws reports failure and lets a later banner retry`() = runTest {
        val start = StartOnce()
        var calls = 0

        assertThat(start.await { calls++; throw IllegalStateException("no sdk") }).isEqualTo(false)
        assertThat(start.isDone).isEqualTo(false)
        assertThat(start.await { done -> calls++; done() }).isEqualTo(true)
        assertThat(calls).isEqualTo(2)
    }
}
