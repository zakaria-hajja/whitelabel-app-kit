package com.whitelabel.core.firebase

import com.whitelabel.core.analytics.Analytics
import com.whitelabel.core.analytics.AnalyticsEvent
import com.whitelabel.core.analytics.CrashReporter
import com.whitelabel.core.analytics.UserProperty
import com.whitelabel.core.consent.ConsentState
import kotlinx.coroutines.flow.StateFlow

/** Forwards events and user properties only once consent is [ConsentState.Resolved]. Earlier ones are dropped, not queued. */
class ConsentGatedAnalytics(
    private val inner: Analytics,
    private val consent: StateFlow<ConsentState>,
) : Analytics {
    override fun track(event: AnalyticsEvent) {
        if (consent.value is ConsentState.Resolved) inner.track(event)
    }

    override fun setUserProperty(property: UserProperty) {
        if (consent.value is ConsentState.Resolved) inner.setUserProperty(property)
    }
}

/** Forwards failures only once consent is [ConsentState.Resolved]. */
class ConsentGatedCrashReporter(
    private val inner: CrashReporter,
    private val consent: StateFlow<ConsentState>,
) : CrashReporter {
    override fun record(throwable: Throwable) {
        if (consent.value is ConsentState.Resolved) inner.record(throwable)
    }
}
