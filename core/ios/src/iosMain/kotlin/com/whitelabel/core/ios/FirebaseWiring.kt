package com.whitelabel.core.ios

import com.whitelabel.core.consent.UmpResult
import com.whitelabel.core.consent.UmpStatus
import com.whitelabel.core.firebase.FirebaseMode

/**
 * The iOS twin of Android's `firebaseModeFor`: [resource] is `WL_FIREBASE` (`on`, `fake` or `off`),
 * [services] whether Swift installed the Firebase pieces, [declared] whether the app's definition has
 * a Firebase service, [fakeMarker] the `-fakeFirebase` debug argument. An app without a Firebase
 * service is always off.
 */
internal fun firebaseModeFor(resource: String, services: Boolean, declared: Boolean, fakeMarker: Boolean): FirebaseMode = when {
    declared && (resource == "fake" || fakeMarker) -> FirebaseMode.FAKE
    resource == "on" && !services -> throw IllegalStateException("firebase is on but no Firebase service was installed")
    !declared -> FirebaseMode.OFF
    resource == "on" -> FirebaseMode.ON
    else -> FirebaseMode.OFF
}

/**
 * What a `-fakeConsent <state>` argument answers: `obtained` (default), `declined` (every purpose
 * refused), `failed` (the gather fails, so null) or `notRequired`. Same mapping as Android's.
 */
internal fun fakeConsentResult(state: String): UmpResult? = when (state) {
    "declined" -> UmpResult(UmpStatus.OBTAINED, canRequestAds = true, purposeConsents = "0")
    "failed" -> null
    "notRequired" -> UmpResult(UmpStatus.NOT_REQUIRED, canRequestAds = true, purposeConsents = null)
    else -> UmpResult(UmpStatus.OBTAINED, canRequestAds = true, purposeConsents = "1111111111")
}
