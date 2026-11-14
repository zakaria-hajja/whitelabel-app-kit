package com.whitelabel.core.shell

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import assertk.assertThat
import assertk.assertions.containsExactly
import org.junit.jupiter.api.Test

class ContentGateLifecycleTest {

    private val owner = object : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test fun `start and stop are forwarded once each, resume and pause are not`() {
        val gate = FakeContentGate()
        owner.registry.addObserver(ContentGateLifecycle(gate))

        owner.registry.currentState = Lifecycle.State.CREATED
        owner.registry.currentState = Lifecycle.State.STARTED
        owner.registry.currentState = Lifecycle.State.RESUMED
        owner.registry.currentState = Lifecycle.State.STARTED
        owner.registry.currentState = Lifecycle.State.CREATED

        assertThat(gate.calls).containsExactly(FakeContentGate.Call.FOREGROUND, FakeContentGate.Call.BACKGROUND)
    }
}
