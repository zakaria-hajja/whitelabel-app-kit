package com.whitelabel.core.shell.model

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test

class ContentGateTest {

    @Test fun `a pending gate keeps the splash even when the shell is ready`() {
        assertThat(shellScreen(showsContent = true, gate = ContentGateState.PENDING)).isEqualTo(ShellScreen.SPLASH)
    }

    @Test fun `a decided gate still waits for the shell`() {
        assertThat(shellScreen(showsContent = false, gate = ContentGateState.LOCKED)).isEqualTo(ShellScreen.SPLASH)
        assertThat(shellScreen(showsContent = false, gate = ContentGateState.OPEN)).isEqualTo(ShellScreen.SPLASH)
        assertThat(shellScreen(showsContent = false, gate = ContentGateState.PENDING)).isEqualTo(ShellScreen.SPLASH)
    }

    @Test fun `a locked gate shows the lock`() {
        assertThat(shellScreen(showsContent = true, gate = ContentGateState.LOCKED)).isEqualTo(ShellScreen.LOCK)
    }

    @Test fun `an open gate shows the content`() {
        assertThat(shellScreen(showsContent = true, gate = ContentGateState.OPEN)).isEqualTo(ShellScreen.CONTENT)
    }

    @Test fun `the lock wiring must be both halves or neither`() {
        assertThat(contentGateProblem(declaresLockScreen = false, bindsGate = false)).isNull()
        assertThat(contentGateProblem(declaresLockScreen = true, bindsGate = true)).isNull()
        assertThat(contentGateProblem(declaresLockScreen = true, bindsGate = false))
            .isEqualTo("contentGate() needs a ContentGate bound in the app's modules")
        assertThat(contentGateProblem(declaresLockScreen = false, bindsGate = true))
            .isEqualTo("a ContentGate is bound but the app definition doesn't call contentGate()")
    }
}
