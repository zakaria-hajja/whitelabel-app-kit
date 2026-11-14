package com.whitelabel.core.ios.mock

import com.whitelabel.core.shell.model.ContentGateState
import kotlin.test.Test
import kotlin.test.assertEquals

/** The UI tests' lock: open at launch, locked on every return to the foreground, open again on unlock. */
class UiTestContentGateTest {

    @Test fun startsOpen() {
        assertEquals(ContentGateState.OPEN, UiTestContentGate().state.value)
    }

    @Test fun foregroundLocksSynchronously() {
        val gate = UiTestContentGate()
        gate.onAppBackground()
        assertEquals(ContentGateState.OPEN, gate.state.value)
        gate.onAppForeground()
        assertEquals(ContentGateState.LOCKED, gate.state.value)
    }

    @Test fun unlockOpens() {
        val gate = UiTestContentGate()
        gate.onAppForeground()
        gate.unlock()
        assertEquals(ContentGateState.OPEN, gate.state.value)
    }
}
