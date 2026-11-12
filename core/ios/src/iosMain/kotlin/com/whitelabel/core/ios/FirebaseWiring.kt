package com.whitelabel.core.ios

import com.whitelabel.core.firebase.FirebaseMode

/**
 * The iOS twin of Android's `firebaseModeFor` (its inputs differ: iOS also needs [declared] and
 * whether Swift installed the services, so it stays per platform): [resource] is `WL_FIREBASE` (`on`, `fake` or `off`),
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
