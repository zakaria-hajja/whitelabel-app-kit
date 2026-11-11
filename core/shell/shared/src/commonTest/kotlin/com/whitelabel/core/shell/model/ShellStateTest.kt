package com.whitelabel.core.shell.model

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.whitelabel.core.domain.SessionState
import kotlin.test.Test

class ShellStateTest {

    @Test fun `nothing shows until the tabs are decided`() {
        assertThat(ShellState(ready = false, session = SessionState.SignedIn).showsContent(hasGate = false)).isFalse()
    }

    @Test fun `a gated app waits for the session`() {
        assertThat(ShellState(ready = true, session = SessionState.Unknown).showsContent(hasGate = true)).isFalse()
        assertThat(ShellState(ready = true, session = SessionState.SignedOut).showsContent(hasGate = true)).isTrue()
        assertThat(ShellState(ready = true, session = SessionState.SignedIn).showsContent(hasGate = true)).isTrue()
    }

    @Test fun `a public app does not wait for the session`() {
        assertThat(ShellState(ready = true, session = SessionState.Unknown).showsContent(hasGate = false)).isTrue()
    }
}
