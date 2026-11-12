package com.whitelabel.core.firebase.google

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.whitelabel.core.analytics.Analytics
import com.whitelabel.core.analytics.AnalyticsEvent

/** [Analytics] that logs to Firebase. Consent gating is applied around it (`ConsentGatedAnalytics`). */
class FirebaseAnalyticsAdapter(private val analytics: FirebaseAnalytics) : Analytics {
    override fun track(event: AnalyticsEvent) {
        val params = Bundle().apply { event.params.forEach { (key, value) -> putString(key, value) } }
        analytics.logEvent(event.name, params)
    }
}
