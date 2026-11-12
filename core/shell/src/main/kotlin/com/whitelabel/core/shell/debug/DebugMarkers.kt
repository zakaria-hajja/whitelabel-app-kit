package com.whitelabel.core.shell.debug

import android.content.Context
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
        FAKE_CONSENT_STATES.firstOrNull { has("$FAKE_CONSENT_PREFIX$it") } ?: "obtained"

    companion object {
        /** Firebase runs as the in-memory fake. */
        const val FAKE_FIREBASE = "fake-firebase"

        /** Analytics and crash collection run in a debug build (they are off there by default). */
        const val ANALYTICS_DEBUG = "analytics-debug"

        /**
         * Forces the EEA consent geography. Read by `:core:consent:google` as its `DEBUG_EEA_MARKER`;
         * the shell can't depend on that module (release builds don't link it), so the literal is repeated.
         */
        const val CONSENT_EEA = "consent-eea"

        const val FAKE_CONSENT_PREFIX = "fake-consent-"
        val FAKE_CONSENT_STATES = listOf("obtained", "declined", "failed", "notRequired")

        fun of(context: Context, debugBuild: Boolean) = DebugMarkers(if (debugBuild) File(context.filesDir, "debug") else null)
    }
}
