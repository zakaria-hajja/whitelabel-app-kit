package com.whitelabel.core.firebase.google

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.whitelabel.core.analytics.Analytics
import com.whitelabel.core.analytics.AnalyticsEvent
import com.whitelabel.core.analytics.UserProperty

/**
 * [Analytics] that logs to Firebase. Consent gating is applied around it (`ConsentGatedAnalytics`).
 * The two calls are passed in so the forwarding is testable without the SDK.
 */
class FirebaseAnalyticsAdapter internal constructor(
    private val logEvent: (name: String, params: Bundle) -> Unit,
    private val sendUserProperty: (name: String, value: String) -> Unit,
) : Analytics {
    constructor(analytics: FirebaseAnalytics) : this(analytics::logEvent, analytics::setUserProperty)

    override fun track(event: AnalyticsEvent) {
        val params = Bundle().apply { event.params.forEach { (key, value) -> putString(key, value) } }
        logEvent(event.name, params)
    }

    override fun setUserProperty(property: UserProperty) = sendUserProperty(property.name, property.value)
}
