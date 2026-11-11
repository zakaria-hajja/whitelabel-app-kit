package com.whitelabel.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Plays the active style pack's feedback for an event; an event the pack doesn't map plays
 * nothing. The system's touch-feedback setting still applies.
 */
@Composable
fun rememberHaptics(): (HapticEvent) -> Unit {
    val feedback = LocalHapticFeedback.current
    val haptics = AppTheme.style.haptics
    return remember(feedback, haptics) {
        { event ->
            when (haptics[event]) {
                HapticPattern.Tick -> feedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
                HapticPattern.Confirm -> feedback.performHapticFeedback(HapticFeedbackType.Confirm)
                HapticPattern.Toggle -> feedback.performHapticFeedback(HapticFeedbackType.ToggleOn)
                null -> Unit
            }
        }
    }
}
