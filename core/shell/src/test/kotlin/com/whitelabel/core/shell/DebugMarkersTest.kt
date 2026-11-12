package com.whitelabel.core.shell

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.whitelabel.core.shell.debug.DebugMarkers
import java.io.File
import java.nio.file.Files
import org.junit.jupiter.api.Test

class DebugMarkersTest {
    private val dir: File = Files.createTempDirectory("markers").toFile()

    @Test fun `a marker file turns its switch on`() {
        File(dir, "analytics-debug").createNewFile()
        assertThat(DebugMarkers(dir).has(DebugMarkers.ANALYTICS_DEBUG)).isTrue()
        assertThat(DebugMarkers(dir).has(DebugMarkers.FAKE_FIREBASE)).isFalse()
    }

    @Test fun `no directory means no markers`() {
        assertThat(DebugMarkers(null).has(DebugMarkers.ANALYTICS_DEBUG)).isFalse()
    }

    @Test fun `the fake consent state comes from its marker and defaults to obtained`() {
        assertThat(DebugMarkers(dir).fakeConsentState()).isEqualTo("obtained")
        File(dir, "fake-consent-failed").createNewFile()
        assertThat(DebugMarkers(dir).fakeConsentState()).isEqualTo("failed")
    }
}
