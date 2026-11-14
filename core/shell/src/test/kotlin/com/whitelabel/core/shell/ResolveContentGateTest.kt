package com.whitelabel.core.shell

import androidx.compose.material3.Text
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import com.whitelabel.core.shell.model.ContentGate
import org.junit.jupiter.api.Test
import org.koin.core.error.InstanceCreationException
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class ResolveContentGateTest {

    private val withLock = appDefinition {
        add(entry("a", RouteA))
        contentGate { Text("Lock") }
    }

    private class NeedsMissing(@Suppress("unused") val missing: Missing) : ContentGate by FakeContentGate()
    private class Missing

    @Test fun `the gate the shell reads is the one the lifecycle drives, even for a factory binding`() {
        val koin = koinApplication { modules(module { factory<ContentGate> { FakeContentGate() } }) }.koin

        val wiring = checkNotNull(resolveContentGate(koin, withLock))
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry.createUnsafe(this)
            override val lifecycle: Lifecycle get() = registry
        }
        owner.registry.addObserver(wiring)
        owner.registry.currentState = Lifecycle.State.STARTED

        assertThat((wiring.gate as FakeContentGate).calls).containsExactly(FakeContentGate.Call.FOREGROUND)
    }

    @Test fun `a gate whose own dependency is missing fails at startup, never runs unlocked`() {
        val koin = koinApplication { modules(module { single<ContentGate> { NeedsMissing(get()) } }) }.koin

        assertFailure { resolveContentGate(koin, withLock) }.isInstanceOf(InstanceCreationException::class)
    }

    @Test fun `no lock screen and no gate is today's shell`() {
        val koin = koinApplication { }.koin

        assertThat(resolveContentGate(koin, appDefinition { add(entry("a", RouteA)) })).isNull()
    }

    @Test fun `a lock screen without a gate fails at startup`() {
        assertFailure { resolveContentGate(koinApplication { }.koin, withLock) }
            .isInstanceOf(IllegalStateException::class)
    }
}
