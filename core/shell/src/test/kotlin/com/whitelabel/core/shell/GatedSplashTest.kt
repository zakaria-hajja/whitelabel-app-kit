package com.whitelabel.core.shell

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.whitelabel.core.shell.model.ShellScreen
import org.junit.jupiter.api.Test

/** Lock first, then consent: on the gate path only the content screen lets launch consent run. */
class GatedSplashTest {

    @Test fun `the splash holds and so does consent`() {
        assertThat(gatedSplash(ShellScreen.SPLASH)).isEqualTo(GatedSplash(keep = true, splashKept = true))
    }

    @Test fun `the lock lets the splash go but still holds consent`() {
        assertThat(gatedSplash(ShellScreen.LOCK)).isEqualTo(GatedSplash(keep = false, splashKept = true))
    }

    @Test fun `the content lets both go`() {
        assertThat(gatedSplash(ShellScreen.CONTENT)).isEqualTo(GatedSplash(keep = false, splashKept = false))
    }

    @Test fun `while locked consent is never gathered, after unlock it is, once`() {
        val locked = gatedSplash(ShellScreen.LOCK)
        assertThat(shouldGatherConsent(needsConsent = true, locked.splashKept, alreadyRequested = false)).isEqualTo(false)
        val open = gatedSplash(ShellScreen.CONTENT)
        assertThat(shouldGatherConsent(needsConsent = true, open.splashKept, alreadyRequested = false)).isEqualTo(true)
        assertThat(shouldGatherConsent(needsConsent = true, open.splashKept, alreadyRequested = true)).isEqualTo(false)
    }
}
