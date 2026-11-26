package com.whitelabel.core.firebase

import com.whitelabel.core.consent.ConsentState

/** What the Firebase SDKs may collect right now. */
data class FirebaseCollection(val analytics: Boolean, val analyticsStorage: Boolean, val crashes: Boolean)

/**
 * Not resolved: nothing. Resolved: analytics and crashes follow the build (debug builds need the switch),
 * whatever the user chose, so a decliner is still counted, but [FirebaseCollection.analyticsStorage] (identifiers)
 * only when the user allowed it.
 *
 * With [analyticsOptIn] (strict opt-in), a user who declined analytics isn't counted at all: analytics
 * collection and storage stay off. Crashes still follow the build.
 */
fun collectionFor(
    consent: ConsentState,
    debugBuild: Boolean,
    debugAnalytics: Boolean,
    analyticsOptIn: Boolean = false,
): FirebaseCollection {
    if (consent !is ConsentState.Resolved) return FirebaseCollection(analytics = false, analyticsStorage = false, crashes = false)
    val on = !debugBuild || debugAnalytics
    val analytics = on && (consent.analyticsAllowed || !analyticsOptIn)
    return FirebaseCollection(analytics = analytics, analyticsStorage = consent.analyticsAllowed, crashes = on)
}

/** Applies a [FirebaseCollection] to the platform SDKs. Implemented on Android and in Swift. */
interface CollectionSwitch {
    fun apply(collection: FirebaseCollection)
}
