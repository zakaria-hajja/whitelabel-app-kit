package com.whitelabel.core.shell

import android.app.Activity
import android.os.Build
import android.view.WindowManager

/** How the app's snapshot is hidden in recents while its content gate asks for it. */
internal enum class RecentsProtection { NONE, NO_SCREENSHOT, FLAG_SECURE }

/**
 * API 33+ can turn off just the recents screenshot. Below that only `FLAG_SECURE` hides it, which
 * also blocks the user's own screenshots (accepted, ADR 0007).
 */
internal fun recentsProtection(sdkInt: Int, on: Boolean): RecentsProtection = when {
    !on -> RecentsProtection.NONE
    sdkInt >= Build.VERSION_CODES.TIRAMISU -> RecentsProtection.NO_SCREENSHOT
    else -> RecentsProtection.FLAG_SECURE
}

/** What the gate does to the window's `FLAG_SECURE`. */
internal enum class SecureFlagChange { ADD, CLEAR, NONE }

/**
 * The gate owns `FLAG_SECURE` only when it set the flag: it adds it when the window doesn't have it,
 * and clears only its own. A flag the app (or another screen) set stays.
 */
internal fun secureFlagChange(protection: RecentsProtection, flagSet: Boolean, setByGate: Boolean): SecureFlagChange = when {
    protection == RecentsProtection.FLAG_SECURE -> if (flagSet) SecureFlagChange.NONE else SecureFlagChange.ADD
    setByGate -> SecureFlagChange.CLEAR
    else -> SecureFlagChange.NONE
}

/** Applies [protection]; [setByGate] says whether the gate set `FLAG_SECURE`. Returns the new value. */
internal fun Activity.applyRecentsProtection(protection: RecentsProtection, setByGate: Boolean): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        setRecentsScreenshotEnabled(protection != RecentsProtection.NO_SCREENSHOT)
    }
    val flagSet = window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
    return when (secureFlagChange(protection, flagSet, setByGate)) {
        SecureFlagChange.ADD -> true.also { window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) }
        SecureFlagChange.CLEAR -> false.also { window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
        SecureFlagChange.NONE -> setByGate
    }
}
