package com.whitelabel.core.reminders

/**
 * Local notifications at a date and time. They survive reboots, clock and time-zone changes and app
 * updates; a reminder missed while the phone was off is shown once if it's less than 12 hours late.
 * Get it from Koin: add `remindersModule(config)` to the app definition with `modules(...)`. Scheduling never asks for the notification permission: see [rememberNotificationPermission].
 */
class Reminders(private val engine: ReminderEngine) {
    /** Schedules [reminder], replacing one with the same id. */
    suspend fun schedule(reminder: Reminder) = engine.schedule(reminder)

    /** Makes the pending reminders whose id starts with [idPrefix] exactly [reminders] (every id must start with it). */
    suspend fun setAll(reminders: List<Reminder>, idPrefix: String) = engine.setAll(reminders, idPrefix)

    suspend fun cancel(id: String) = engine.cancel(id)

    suspend fun cancelAll() = engine.cancelAll()

    suspend fun pending(): List<Reminder> = engine.pending()
}
