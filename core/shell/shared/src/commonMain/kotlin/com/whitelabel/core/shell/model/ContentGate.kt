package com.whitelabel.core.shell.model

import kotlinx.coroutines.flow.StateFlow

/** What the app's lock says right now. */
enum class ContentGateState { PENDING, LOCKED, OPEN }

/**
 * An app-level gate in front of the whole app (an app lock). The app binds one in Koin; the shell
 * reads [state], reports the app's foreground/background, and applies [protectsAppSwitcher].
 * The shell never unlocks: the app's lock screen does that through its own implementation.
 *
 * Resolved from Koin while the app starts (Android: `Application.onCreate`), so creating it must
 * be cheap and must not throw.
 */
interface ContentGate {
    /**
     * PENDING until the app knows (e.g. its lock setting is loading): the splash stays. It must leave
     * PENDING quickly and **never return to it**. It is read on the main thread before each splash frame.
     */
    val state: StateFlow<ContentGateState>

    /** Hide the app's snapshot in the app switcher: true while PENDING and whenever the lock is on. */
    val protectsAppSwitcher: StateFlow<Boolean>

    /**
     * The app returned to the foreground (process level). Update [state] synchronously in this call
     * (no suspend, no dispatch), so the first frame after returning is already the lock.
     * Android also calls it once at launch, and iOS doesn't: the result must not depend on that.
     */
    fun onAppForeground()

    /** The app went to the background (process level; not on rotation, not for one iPad scene). */
    fun onAppBackground()
}

/** The gated shell's root screen. */
enum class ShellScreen { SPLASH, LOCK, CONTENT }

/** With a gate: SPLASH until the shell shows content and the gate isn't PENDING; then LOCK or CONTENT. */
fun shellScreen(showsContent: Boolean, gate: ContentGateState): ShellScreen = when {
    !showsContent || gate == ContentGateState.PENDING -> ShellScreen.SPLASH
    gate == ContentGateState.LOCKED -> ShellScreen.LOCK
    else -> ShellScreen.CONTENT
}

/** Why the app's lock wiring is wrong, or null: a lock screen without a bound gate, or the reverse. */
fun contentGateProblem(declaresLockScreen: Boolean, bindsGate: Boolean): String? = when {
    declaresLockScreen && !bindsGate -> "contentGate() needs a ContentGate bound in the app's modules"
    !declaresLockScreen && bindsGate -> "a ContentGate is bound but the app definition doesn't call contentGate()"
    else -> null
}
