package com.whitelabel.core.firebase

import com.whitelabel.core.analytics.Analytics
import com.whitelabel.core.analytics.AnalyticsEvent
import com.whitelabel.core.analytics.CrashReporter
import com.whitelabel.core.analytics.UserProperty
import com.whitelabel.core.consent.ConsentState
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Forwards events and user properties only once consent is [ConsentState.Resolved].
 *
 * - Events are moments: one tracked before consent resolves is dropped, not queued.
 * - User properties are state (last write wins), so one set while consent is [ConsentState.Unknown]
 *   waits: the latest value per name is kept and sent once consent resolves with analytics allowed,
 *   or discarded when it resolves with analytics declined or [fails][ConsentState.Failed]. After that
 *   the gate is the same as for events. [scope] (the
 *   application scope) runs the flush, so a property set once at app start reaches the SDK even
 *   when nothing is set after consent resolves.
 */
class ConsentGatedAnalytics(
    private val inner: Analytics,
    private val consent: StateFlow<ConsentState>,
    scope: CoroutineScope,
) : Analytics {
    private val lock = SynchronizedObject()

    // Waiting user properties by name, in first-set order. Null once the first resolution was handled.
    // At most 25 names (Firebase's per-project limit), so it stays small.
    private var pending: LinkedHashMap<String, UserProperty>? = LinkedHashMap()

    init {
        scope.launch {
            val resolved = consent.first { it !is ConsentState.Unknown }
            synchronized(lock) { settleLocked(resolved) }
        }
    }

    override fun track(event: AnalyticsEvent) {
        if (consent.value is ConsentState.Resolved) inner.track(event)
    }

    override fun setUserProperty(property: UserProperty) {
        synchronized(lock) {
            val state = consent.value
            val waiting = pending
            if (waiting != null && state is ConsentState.Unknown) {
                waiting[property.name] = property
                return
            }
            // Consent resolved before the flush collector ran: flush first, so this newer value lands last.
            if (waiting != null) settleLocked(state)
            if (state is ConsentState.Resolved) inner.setUserProperty(property)
        }
    }

    // Called with the lock held; it forwards inside the lock, so a queued value can't overtake a newer
    // one for the same name.
    private fun settleLocked(state: ConsentState) {
        val waiting = pending ?: return
        pending = null
        // Only with analytics allowed: with collection off the SDK can still store a property and send it later.
        if (state is ConsentState.Resolved && state.analyticsAllowed) waiting.values.forEach(inner::setUserProperty)
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
