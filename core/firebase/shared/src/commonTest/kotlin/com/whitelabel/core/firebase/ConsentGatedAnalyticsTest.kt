package com.whitelabel.core.firebase

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import com.whitelabel.core.analytics.Analytics
import com.whitelabel.core.analytics.AnalyticsEvent
import com.whitelabel.core.analytics.CrashReporter
import com.whitelabel.core.consent.ConsentState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test

class ConsentGatedAnalyticsTest {
    private class Recorder : Analytics, CrashReporter {
        val events = mutableListOf<AnalyticsEvent>()
        val crashes = mutableListOf<Throwable>()
        override fun track(event: AnalyticsEvent) { events += event }
        override fun record(throwable: Throwable) { crashes += throwable }
    }

    @Test fun `events before consent is resolved are dropped and not queued`() {
        val inner = Recorder()
        val state = MutableStateFlow<ConsentState>(ConsentState.Unknown)
        val gated = ConsentGatedAnalytics(inner, state)
        gated.track(AnalyticsEvent("one"))
        gated.track(AnalyticsEvent("two"))
        state.value = ConsentState.Resolved(true, true)
        gated.track(AnalyticsEvent("three"))
        assertThat(inner.events.map { it.name }).containsExactly("three")
    }

    @Test fun `failed consent drops events`() {
        val inner = Recorder()
        ConsentGatedAnalytics(inner, MutableStateFlow(ConsentState.Failed)).track(AnalyticsEvent("one"))
        assertThat(inner.events).isEmpty()
    }

    @Test fun `crashes follow the same gate`() {
        val inner = Recorder()
        val state = MutableStateFlow<ConsentState>(ConsentState.Unknown)
        val gated = ConsentGatedCrashReporter(inner, state)
        val early = RuntimeException("early")
        val late = RuntimeException("late")
        gated.record(early)
        state.value = ConsentState.Resolved(false, false)
        gated.record(late)
        assertThat(inner.crashes).containsExactly(late)
    }
}
