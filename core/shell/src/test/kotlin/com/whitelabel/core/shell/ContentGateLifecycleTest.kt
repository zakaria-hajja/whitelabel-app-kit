package com.whitelabel.core.shell

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import com.whitelabel.core.shell.model.ContentGateState
import org.junit.jupiter.api.Test

class ContentGateLifecycleTest {

    private val owner = object : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test fun `start and stop are forwarded once each, resume and pause are not`() {
        val gate = FakeContentGate()
        owner.registry.addObserver(ContentGateLifecycle(gate, RecordingWindows(gate)))

        owner.registry.currentState = Lifecycle.State.CREATED
        owner.registry.currentState = Lifecycle.State.STARTED
        owner.registry.currentState = Lifecycle.State.RESUMED
        owner.registry.currentState = Lifecycle.State.STARTED
        owner.registry.currentState = Lifecycle.State.CREATED

        assertThat(gate.calls).containsExactly(FakeContentGate.Call.FOREGROUND, FakeContentGate.Call.BACKGROUND)
    }

    /** Records the window cover's calls into the same list as the gate's, so the order shows. */
    private class RecordingWindows(private val gate: FakeContentGate) : AppWindows {
        val calls = mutableListOf<String>()
        override fun cover() { calls += "cover after ${gate.calls.lastOrNull()}" }
        override fun uncover() { calls += "uncover while ${gate.state.value}" }
    }

    private fun backgroundThenReturn(gate: FakeContentGate, windows: AppWindows, lockOnReturn: Boolean) {
        owner.registry.addObserver(ContentGateLifecycle(gate, windows))
        owner.registry.currentState = Lifecycle.State.STARTED
        owner.registry.currentState = Lifecycle.State.CREATED
        if (lockOnReturn) gate.lockOnNextForeground = true
        owner.registry.currentState = Lifecycle.State.STARTED
    }

    @Test fun `the app's windows are covered when it goes to the background`() {
        val gate = FakeContentGate()
        val windows = RecordingWindows(gate)
        owner.registry.addObserver(ContentGateLifecycle(gate, windows))
        owner.registry.currentState = Lifecycle.State.STARTED
        owner.registry.currentState = Lifecycle.State.CREATED

        assertThat(windows.calls.filter { it.startsWith("cover") }).containsExactly("cover after BACKGROUND")
    }

    @Test fun `a return the gate lets through uncovers the windows after the gate decided`() {
        val gate = FakeContentGate()
        val windows = RecordingWindows(gate)
        backgroundThenReturn(gate, windows, lockOnReturn = false)

        assertThat(windows.calls.last()).isEqualTo("uncover while OPEN")
    }

    @Test fun `a return the gate locks keeps the windows covered`() {
        val gate = FakeContentGate()
        val windows = RecordingWindows(gate)
        backgroundThenReturn(gate, windows, lockOnReturn = true)

        assertThat(windows.calls.last()).isEqualTo("cover after BACKGROUND")
    }

    @Test fun `the content showing again uncovers the windows`() {
        val gate = FakeContentGate(ContentGateState.LOCKED)
        val windows = RecordingWindows(gate)
        val lifecycle = ContentGateLifecycle(gate, windows)
        gate.state.value = ContentGateState.OPEN
        lifecycle.onContentShown()

        assertThat(windows.calls).containsExactly("uncover while OPEN")
    }
}
