package com.whitelabel.core.shell.debug

import android.content.Context
import com.whitelabel.core.consent.DebugConsent
import java.io.File

/**
 * Debug switches kept as empty marker files under `filesDir/debug`, so a developer or a test can
 * change behavior with `adb shell run-as <app> touch files/debug/<name>` and no rebuild. Debuggable
 * builds only: a release build has no markers.
 */
internal class DebugMarkers(private val dir: File?) {

    fun has(name: String): Boolean = dir != null && File(dir, name).exists()

    /** The state named by a `fake-consent-<state>` marker; `obtained` when there is none. */
    fun fakeConsentState(): String =
        DebugConsent.FAKE_STATES.firstOrNull { has("$FAKE_CONSENT_PREFIX$it") } ?: DebugConsent.DEFAULT_FAKE_STATE

    companion object {
        /** Firebase runs as the in-memory fake. */
        const val FAKE_FIREBASE = "fake-firebase"

        /** Analytics and crash collection run in a debug build (they are off there by default). */
        const val ANALYTICS_DEBUG = "analytics-debug"

        // The EEA consent marker is DebugConsent.EEA_MARKER, read by :core:consent:google.

        /** Followed by one of DebugConsent.FAKE_STATES. */
        const val FAKE_CONSENT_PREFIX = "fake-consent-"

        fun of(context: Context, debugBuild: Boolean) = DebugMarkers(if (debugBuild) File(context.filesDir, "debug") else null)
    }
}
