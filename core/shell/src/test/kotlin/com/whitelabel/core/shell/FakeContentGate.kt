package com.whitelabel.core.shell

import com.whitelabel.core.shell.model.ContentGate
import com.whitelabel.core.shell.model.ContentGateState
import kotlinx.coroutines.flow.MutableStateFlow

/** Records what the shell reports; tests set [state] directly. */
class FakeContentGate(initial: ContentGateState = ContentGateState.OPEN) : ContentGate {
    enum class Call { FOREGROUND, BACKGROUND }

    val calls = mutableListOf<Call>()
    override val state = MutableStateFlow(initial)
    override val protectsAppSwitcher = MutableStateFlow(false)

    /** The next [onAppForeground] locks, as a gate past its grace period does. */
    var lockOnNextForeground = false

    override fun onAppForeground() {
        calls += Call.FOREGROUND
        if (lockOnNextForeground) state.value = ContentGateState.LOCKED
        lockOnNextForeground = false
    }

    override fun onAppBackground() {
        calls += Call.BACKGROUND
    }
}
