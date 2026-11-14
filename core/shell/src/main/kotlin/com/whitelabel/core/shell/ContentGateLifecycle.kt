package com.whitelabel.core.shell

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.whitelabel.core.shell.model.ContentGate
import com.whitelabel.core.shell.model.ContentGateState
import com.whitelabel.core.shell.model.contentGateProblem
import org.koin.core.Koin

/**
 * Reports the app's foreground and background to [gate]. Observes the **process** lifecycle: it
 * ignores rotation (ON_STOP is debounced) and holds across every activity of the app. Process
 * ON_START comes before the first activity's `onResume`, so the gate decides before the activity's
 * first frame. The app's other windows (dialogs, sheets) draw earlier than that on return, so they are
 * covered while in the background and uncovered only once the gate is OPEN ([AppWindowCover]).
 */
internal class ContentGateLifecycle(
    val gate: ContentGate,
    private val windows: AppWindows = AppWindowCover(),
) : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) {
        gate.onAppForeground()
        if (gate.state.value == ContentGateState.OPEN) windows.uncover()
    }

    override fun onStop(owner: LifecycleOwner) {
        gate.onAppBackground()
        windows.cover()
    }

    /** The gated shell shows the content (the unlock after a relock): windows that outlived the lock show again. */
    fun onContentShown() = windows.uncover()
}

/**
 * Resolves the app's gate **once** for the process and wraps it in the lifecycle that drives it.
 * The activity reads this same instance ([FoundationApplication.contentGate]), never Koin again, so a
 * `factory` binding can't split the lock the user sees from the one the lifecycle relocks. A gate
 * that fails to build (Koin's `InstanceCreationException`) or a half-done wiring fails here, at
 * startup: the app never runs unlocked because of wiring.
 */
internal fun resolveContentGate(koin: Koin, definition: AppDefinition): ContentGateLifecycle? {
    val gate = koin.getOrNull<ContentGate>()
    checkContentGate(definition, gate)
    return gate?.let(::ContentGateLifecycle)
}

/** Fails at startup when the app's lock wiring is half done: a lock screen without a gate, or the reverse. */
internal fun checkContentGate(definition: AppDefinition, gate: ContentGate?) {
    contentGateProblem(declaresLockScreen = definition.lockScreen != null, bindsGate = gate != null)
        ?.let { throw IllegalStateException(it) }
}

/**
 * Adds [observer] to the process lifecycle. Lifecycle observers are main-thread only, and tests
 * start the foundation from the instrumentation thread, so off the main thread it is posted there.
 * Adding it while the process is STARTED dispatches ON_START at once: Android's launch call.
 */
internal fun registerContentGateLifecycle(observer: ContentGateLifecycle) = onMainThread {
    ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
}

internal fun unregisterContentGateLifecycle(observer: ContentGateLifecycle) = onMainThread {
    ProcessLifecycleOwner.get().lifecycle.removeObserver(observer)
}

private fun onMainThread(block: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) block() else Handler(Looper.getMainLooper()).post(block)
}
