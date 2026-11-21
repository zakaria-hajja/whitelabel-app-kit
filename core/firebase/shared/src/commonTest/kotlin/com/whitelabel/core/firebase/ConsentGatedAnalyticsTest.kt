package com.whitelabel.core.firebase

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isEmpty
import com.whitelabel.core.analytics.Analytics
import com.whitelabel.core.analytics.AnalyticsEvent
import com.whitelabel.core.analytics.CrashReporter
import com.whitelabel.core.analytics.RecordingAnalytics
import com.whitelabel.core.analytics.UserProperty
import com.whitelabel.core.consent.ConsentState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
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
        val gated = ConsentGatedAnalytics(inner, state, TestScope())
        gated.track(AnalyticsEvent("one"))
        gated.track(AnalyticsEvent("two"))
        state.value = ConsentState.Resolved(true, true)
        gated.track(AnalyticsEvent("three"))
        assertThat(inner.events.map { it.name }).containsExactly("three")
    }

    @Test fun `failed consent drops events`() {
        val inner = Recorder()
        ConsentGatedAnalytics(inner, MutableStateFlow(ConsentState.Failed), TestScope()).track(AnalyticsEvent("one"))
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

    // User properties are state, not moments: one set at app start while consent is Unknown must still reach the SDK.

    @Test fun `user properties set while consent is unknown wait and are flushed when it resolves`() = runTest {
        val inner = RecordingAnalytics()
        val state = MutableStateFlow<ConsentState>(ConsentState.Unknown)
        val gated = ConsentGatedAnalytics(inner, state, backgroundScope.plus(UnconfinedTestDispatcher(testScheduler)))
        gated.setUserProperty(UserProperty("bucket", "1_2"))
        gated.setUserProperty(UserProperty("streak", "none"))
        assertThat(inner.userProperties).isEmpty()

        state.value = ConsentState.Resolved(canRequestAds = false, analyticsAllowed = true)

        assertThat(inner.userProperties).containsExactlyInAnyOrder(
            UserProperty("bucket", "1_2"),
            UserProperty("streak", "none"),
        )
    }

    @Test fun `a name set twice while consent is unknown is flushed once with its latest value`() = runTest {
        val inner = RecordingAnalytics()
        val state = MutableStateFlow<ConsentState>(ConsentState.Unknown)
        val gated = ConsentGatedAnalytics(inner, state, backgroundScope.plus(UnconfinedTestDispatcher(testScheduler)))
        gated.setUserProperty(UserProperty("bucket", "early"))
        gated.setUserProperty(UserProperty("bucket", "late"))

        state.value = ConsentState.Resolved(canRequestAds = true, analyticsAllowed = true)

        assertThat(inner.userProperties).containsExactly(UserProperty("bucket", "late"))
    }

    @Test fun `queued user properties are discarded when consent fails and never sent later`() = runTest {
        val inner = RecordingAnalytics()
        val state = MutableStateFlow<ConsentState>(ConsentState.Unknown)
        val gated = ConsentGatedAnalytics(inner, state, backgroundScope.plus(UnconfinedTestDispatcher(testScheduler)))
        gated.setUserProperty(UserProperty("bucket", "early"))

        state.value = ConsentState.Failed
        gated.setUserProperty(UserProperty("bucket", "while_failed"))
        // The privacy options form can still resolve consent later in the launch.
        state.value = ConsentState.Resolved(canRequestAds = true, analyticsAllowed = true)

        assertThat(inner.userProperties).isEmpty()
    }

    @Test fun `queued user properties are discarded when consent resolves with analytics declined`() = runTest {
        val inner = RecordingAnalytics()
        val state = MutableStateFlow<ConsentState>(ConsentState.Unknown)
        val gated = ConsentGatedAnalytics(inner, state, backgroundScope.plus(UnconfinedTestDispatcher(testScheduler)))
        gated.setUserProperty(UserProperty("bucket", "early"))

        // With collection off the SDK could still store a property locally and send it later.
        state.value = ConsentState.Resolved(canRequestAds = true, analyticsAllowed = false)
        assertThat(inner.userProperties).isEmpty()

        // Allowing analytics later in the launch does not bring the discarded value back.
        state.value = ConsentState.Resolved(canRequestAds = true, analyticsAllowed = true)
        assertThat(inner.userProperties).isEmpty()
    }

    @Test fun `a set after analytics was declined but before the flush ran discards the queue`() = runTest {
        val inner = RecordingAnalytics()
        val state = MutableStateFlow<ConsentState>(ConsentState.Unknown)
        val gated = ConsentGatedAnalytics(inner, state, backgroundScope.plus(StandardTestDispatcher(testScheduler)))
        gated.setUserProperty(UserProperty("bucket", "queued"))
        state.value = ConsentState.Resolved(canRequestAds = false, analyticsAllowed = false)

        // After resolution the gate is unchanged: a Resolved state forwards the new call itself.
        gated.setUserProperty(UserProperty("streak", "after"))
        advanceUntilIdle()

        assertThat(inner.userProperties).containsExactly(UserProperty("streak", "after"))
    }

    @Test fun `user properties after consent resolved are forwarded at once`() = runTest {
        val inner = RecordingAnalytics()
        val state = MutableStateFlow<ConsentState>(ConsentState.Resolved(canRequestAds = false, analyticsAllowed = true))
        val gated = ConsentGatedAnalytics(inner, state, backgroundScope.plus(UnconfinedTestDispatcher(testScheduler)))
        gated.setUserProperty(UserProperty("bucket", "a"))
        gated.setUserProperty(UserProperty("bucket", "b"))
        assertThat(inner.userProperties).containsExactly(UserProperty("bucket", "a"), UserProperty("bucket", "b"))
    }

    @Test fun `failed consent drops user properties`() = runTest {
        val inner = RecordingAnalytics()
        ConsentGatedAnalytics(inner, MutableStateFlow(ConsentState.Failed), backgroundScope)
            .setUserProperty(UserProperty("bucket", "x"))
        assertThat(inner.userProperties).isEmpty()
    }

    @Test fun `a later consent change does not send the flushed properties again`() = runTest {
        val inner = RecordingAnalytics()
        val state = MutableStateFlow<ConsentState>(ConsentState.Unknown)
        val gated = ConsentGatedAnalytics(inner, state, backgroundScope.plus(UnconfinedTestDispatcher(testScheduler)))
        gated.setUserProperty(UserProperty("bucket", "x"))
        state.value = ConsentState.Resolved(canRequestAds = true, analyticsAllowed = true)
        // A withdrawal from the privacy options; the SDK's collection switch handles it, as it does for events.
        state.value = ConsentState.Resolved(canRequestAds = false, analyticsAllowed = false)
        gated.setUserProperty(UserProperty("streak", "y"))
        assertThat(inner.userProperties).containsExactly(UserProperty("bucket", "x"), UserProperty("streak", "y"))
    }

    @Test fun `a set after consent resolved but before the flush ran keeps the newer value last`() = runTest {
        val inner = RecordingAnalytics()
        val state = MutableStateFlow<ConsentState>(ConsentState.Unknown)
        // A standard dispatcher: the flush collector has not run yet when the next call arrives.
        val gated = ConsentGatedAnalytics(inner, state, backgroundScope.plus(StandardTestDispatcher(testScheduler)))
        gated.setUserProperty(UserProperty("bucket", "old"))
        gated.setUserProperty(UserProperty("streak", "s"))
        state.value = ConsentState.Resolved(canRequestAds = true, analyticsAllowed = true)

        gated.setUserProperty(UserProperty("bucket", "new"))
        advanceUntilIdle()

        assertThat(inner.userPropertyValues).isEqualTo(mapOf("bucket" to "new", "streak" to "s"))
        assertThat(inner.userProperties.last()).isEqualTo(UserProperty("bucket", "new"))
        assertThat(inner.userProperties).hasSize(3)
    }

    @Test fun `events set while consent is unknown are still dropped when user properties are queued`() = runTest {
        val inner = RecordingAnalytics()
        val state = MutableStateFlow<ConsentState>(ConsentState.Unknown)
        val gated = ConsentGatedAnalytics(inner, state, backgroundScope.plus(UnconfinedTestDispatcher(testScheduler)))
        gated.track(AnalyticsEvent("early"))
        gated.setUserProperty(UserProperty("bucket", "x"))
        state.value = ConsentState.Resolved(canRequestAds = true, analyticsAllowed = true)
        assertThat(inner.events).isEmpty()
        assertThat(inner.userProperties).containsExactly(UserProperty("bucket", "x"))
    }
}
