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

internal fun Activity.applyRecentsProtection(protection: RecentsProtection) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        setRecentsScreenshotEnabled(protection != RecentsProtection.NO_SCREENSHOT)
    }
    if (protection == RecentsProtection.FLAG_SECURE) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
