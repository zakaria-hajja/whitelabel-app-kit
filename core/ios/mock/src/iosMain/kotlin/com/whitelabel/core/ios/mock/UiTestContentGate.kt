package com.whitelabel.core.ios.mock

import com.whitelabel.core.shell.model.ContentGate
import com.whitelabel.core.shell.model.ContentGateState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.mp.KoinPlatform

/**
 * Debug only, for UI tests of the kit's content gate: open at launch, LOCKED on every return to the
 * foreground (no grace period), open again on [unlock]. Bound by [IosMock.uiTestContentGateModule].
 */
class UiTestContentGate : ContentGate {
    private val mutableState = MutableStateFlow(ContentGateState.OPEN)
    override val state: StateFlow<ContentGateState> = mutableState
    override val protectsAppSwitcher: StateFlow<Boolean> = MutableStateFlow(false)

    override fun onAppForeground() {
        mutableState.value = ContentGateState.LOCKED
    }

    override fun onAppBackground() = Unit

    fun unlock() {
        mutableState.value = ContentGateState.OPEN
    }
}

/** The module an app passes (Debug, UI tests only) to run behind [UiTestContentGate]. */
fun IosMock.uiTestContentGateModule(): Module = module { single<ContentGate> { UiTestContentGate() } }

/** Unlocks the running graph's [UiTestContentGate] (the UI tests' lock screen calls it). */
fun IosMock.unlockUiTestContentGate() = (KoinPlatform.getKoin().get<ContentGate>() as UiTestContentGate).unlock()
