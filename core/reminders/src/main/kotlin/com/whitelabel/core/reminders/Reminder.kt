package com.whitelabel.core.reminders

import java.time.LocalDateTime

/**
 * A local notification the app wants shown at [at].
 *
 * @property id app-chosen and stable (e.g. `"netflix:renewal"`); scheduling the same id again replaces it.
 * @property at a local date and time, interpreted in the device's time zone when the alarm is armed,
 *   so a reminder at 09:00 stays at 09:00 after the user travels.
 * @property channel a channel id from the app's `ReminderConfig`; unknown ids fall back to the default channel.
 * @property deepLink opened when the notification is tapped; null opens the app.
 */
data class Reminder(
    val id: String,
    val at: LocalDateTime,
    val channel: String,
    val title: String,
    val text: String,
    val deepLink: String?,
)
