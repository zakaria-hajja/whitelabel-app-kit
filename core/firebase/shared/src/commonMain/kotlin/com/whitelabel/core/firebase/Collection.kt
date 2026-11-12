package com.whitelabel.core.firebase

import com.whitelabel.core.consent.ConsentState

/** What the Firebase SDKs may collect right now. */
data class Collection(val analytics: Boolean, val analyticsStorage: Boolean, val crashes: Boolean)

/**
 * Not resolved: nothing. Resolved: analytics and crashes follow the build (debug builds need the switch),
 * whatever the user chose, so a decliner is still counted, but [Collection.analyticsStorage] (identifiers)
 * only when the user allowed it.
 */
fun collectionFor(consent: ConsentState, debugBuild: Boolean, debugAnalytics: Boolean): Collection {
    if (consent !is ConsentState.Resolved) return Collection(analytics = false, analyticsStorage = false, crashes = false)
    val on = !debugBuild || debugAnalytics
    return Collection(analytics = on, analyticsStorage = consent.analyticsAllowed, crashes = on)
}

/** Applies a [Collection] to the platform SDKs. Implemented on Android and in Swift. */
interface CollectionSwitch {
    fun apply(collection: Collection)
}
