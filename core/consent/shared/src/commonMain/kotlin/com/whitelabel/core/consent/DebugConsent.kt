package com.whitelabel.core.consent

/** Debug-only consent switches, named once for both platforms. */
object DebugConsent {
    /**
     * Android's marker file (under `filesDir/debug`) that forces UMP's EEA geography and resets the
     * stored consent before gathering. iOS uses the `-consentEEA` launch argument.
     */
    const val EEA_MARKER = "consent-eea"

    /** What the fake consent answers when nothing says otherwise. */
    const val DEFAULT_FAKE_STATE = "obtained"

    /** The states a fake consent can answer; see [fakeConsentResult]. */
    val FAKE_STATES = listOf(DEFAULT_FAKE_STATE, "declined", "failed", "notRequired")
}

/**
 * What the fake consent answers for a [DebugConsent.FAKE_STATES] name: `obtained` (also for an unknown
 * name), `declined` (the form was answered with every purpose refused), `failed` (the gather fails, so
 * null) or `notRequired`. Android reads the name from a `fake-consent-<state>` marker, iOS from
 * `-fakeConsent <state>`.
 */
fun fakeConsentResult(state: String): UmpResult? = when (state) {
    "declined" -> UmpResult(UmpStatus.OBTAINED, canRequestAds = true, purposeConsents = "0")
    "failed" -> null
    "notRequired" -> UmpResult(UmpStatus.NOT_REQUIRED, canRequestAds = true, purposeConsents = null)
    else -> UmpResult(UmpStatus.OBTAINED, canRequestAds = true, purposeConsents = "1111111111")
}
