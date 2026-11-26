package com.whitelabel.core.firebase

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import com.whitelabel.core.analytics.AnalyticsEvent
import com.whitelabel.core.analytics.RecordingAnalytics
import com.whitelabel.core.analytics.UserProperty
import com.whitelabel.core.consent.ConsentState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** [ConsentGatedAnalytics] with strict opt-in: a user who declined analytics sends nothing at all. */
@OptIn(ExperimentalCoroutinesApi::class)
class StrictOptInAnalyticsTest {
    private val allowed = ConsentState.Resolved(canRequestAds = true, analyticsAllowed = true)
    private val declined = ConsentState.Resolved(canRequestAds = true, analyticsAllowed = false)

    private fun TestScope.gate(inner: RecordingAnalytics, state: MutableStateFlow<ConsentState>, scope: CoroutineScope = backgroundScope.plus(UnconfinedTestDispatcher(testScheduler))) =
        ConsentGatedAnalytics(inner, state, scope, analyticsOptIn = true)

    @Test fun `a decline sends no events before or after it resolves`() = runTest {
        val inner = RecordingAnalytics()
        val state = MutableStateFlow<ConsentState>(ConsentState.Unknown)
        val gated = gate(inner, state)
        gated.track(AnalyticsEvent("before"))
        state.value = declined
        gated.track(AnalyticsEvent("after"))
        assertThat(inner.events).isEmpty()
    }

    @Test fun `a decline sends no user properties whether queued or set after it resolves`() = runTest {
        val inner = RecordingAnalytics()
        val state = MutableStateFlow<ConsentState>(ConsentState.Unknown)
        val gated = gate(inner, state)
        gated.setUserProperty(UserProperty("bucket", "queued"))
        state.value = declined
        gated.setUserProperty(UserProperty("streak", "after"))
        assertThat(inner.userProperties).isEmpty()
    }

    @Test fun `a set after the decline but before the flush ran sends nothing`() = runTest {
        val inner = RecordingAnalytics()
        val state = MutableStateFlow<ConsentState>(ConsentState.Unknown)
        val gated = gate(inner, state, backgroundScope.plus(StandardTestDispatcher(testScheduler)))
        gated.setUserProperty(UserProperty("bucket", "queued"))
        state.value = declined
        gated.setUserProperty(UserProperty("streak", "after"))
        advanceUntilIdle()
        assertThat(inner.userProperties).isEmpty()
    }

    @Test fun `allowing analytics sends as without the flag`() = runTest {
        val inner = RecordingAnalytics()
        val state = MutableStateFlow<ConsentState>(ConsentState.Unknown)
        val gated = gate(inner, state)
        gated.setUserProperty(UserProperty("bucket", "queued"))
        state.value = allowed
        gated.track(AnalyticsEvent("one"))
        gated.setUserProperty(UserProperty("streak", "after"))
        assertThat(inner.events.map { it.name }).containsExactly("one")
        assertThat(inner.userProperties).containsExactly(UserProperty("bucket", "queued"), UserProperty("streak", "after"))
    }

    @Test fun `withdrawing consent later stops sending`() = runTest {
        val inner = RecordingAnalytics()
        val state = MutableStateFlow<ConsentState>(allowed)
        val gated = gate(inner, state)
        gated.track(AnalyticsEvent("while_allowed"))
        gated.setUserProperty(UserProperty("bucket", "while_allowed"))
        state.value = declined
        gated.track(AnalyticsEvent("after_withdrawal"))
        gated.setUserProperty(UserProperty("bucket", "after_withdrawal"))
        assertThat(inner.events.map { it.name }).containsExactly("while_allowed")
        assertThat(inner.userProperties).containsExactly(UserProperty("bucket", "while_allowed"))
    }

    @Test fun `granting consent later starts sending`() = runTest {
        val inner = RecordingAnalytics()
        val state = MutableStateFlow<ConsentState>(declined)
        val gated = gate(inner, state)
        gated.track(AnalyticsEvent("while_declined"))
        gated.setUserProperty(UserProperty("bucket", "while_declined"))
        state.value = allowed
        gated.track(AnalyticsEvent("after_grant"))
        gated.setUserProperty(UserProperty("bucket", "after_grant"))
        assertThat(inner.events.map { it.name }).containsExactly("after_grant")
        assertThat(inner.userProperties).containsExactly(UserProperty("bucket", "after_grant"))
    }

    @Test fun `failed consent sends nothing`() = runTest {
        val inner = RecordingAnalytics()
        val gated = gate(inner, MutableStateFlow(ConsentState.Failed))
        gated.track(AnalyticsEvent("one"))
        gated.setUserProperty(UserProperty("bucket", "x"))
        assertThat(inner.events).isEmpty()
        assertThat(inner.userProperties).isEmpty()
    }

    /**
     * A consent flow whose collector is handed [emitted] values while [value] has already moved on: the state
     * changing again between the flush collector's `first {}` and its taking the lock.
     */
    @OptIn(ExperimentalForInheritanceCoroutinesApi::class)
    private class SkewedConsent(var current: ConsentState) : StateFlow<ConsentState> {
        val emitted = MutableSharedFlow<ConsentState>()
        override val value: ConsentState get() = current
        override val replayCache: List<ConsentState> get() = listOf(current)
        override suspend fun collect(collector: FlowCollector<ConsentState>): Nothing {
            emitted.collect(collector)
            awaitCancellation()
        }
    }

    @Test fun `the flush follows the consent when it takes the lock not the answer it first saw`() = runTest {
        val inner = RecordingAnalytics()
        val consent = SkewedConsent(ConsentState.Unknown)
        val gated = ConsentGatedAnalytics(inner, consent, backgroundScope.plus(UnconfinedTestDispatcher(testScheduler)), analyticsOptIn = true)
        gated.setUserProperty(UserProperty("bucket", "queued"))

        // Allowed, then declined before the collector ran: the queue must follow the decline.
        consent.current = declined
        consent.emitted.emit(allowed)

        assertThat(inner.userProperties).isEmpty()
    }
}
