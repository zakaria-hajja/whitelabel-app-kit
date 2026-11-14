package com.whitelabel.core.shell

import androidx.compose.material3.Text
import assertk.assertFailure
import assertk.assertions.hasMessage
import assertk.assertions.isInstanceOf
import org.junit.jupiter.api.Test

class CheckContentGateTest {

    private fun definition(withLock: Boolean) = appDefinition {
        add(entry("a", RouteA))
        if (withLock) contentGate { Text("Lock") }
    }

    @Test fun `a lock screen without a bound gate fails at startup`() {
        assertFailure { checkContentGate(definition(withLock = true), gate = null) }
            .isInstanceOf(IllegalStateException::class)
            .hasMessage("contentGate() needs a ContentGate bound in the app's modules")
    }

    @Test fun `a bound gate without a lock screen fails at startup`() {
        assertFailure { checkContentGate(definition(withLock = false), FakeContentGate()) }
            .isInstanceOf(IllegalStateException::class)
            .hasMessage("a ContentGate is bound but the app definition doesn't call contentGate()")
    }

    @Test fun `neither half passes, and so do both`() {
        checkContentGate(definition(withLock = false), gate = null)
        checkContentGate(definition(withLock = true), FakeContentGate())
    }
}
