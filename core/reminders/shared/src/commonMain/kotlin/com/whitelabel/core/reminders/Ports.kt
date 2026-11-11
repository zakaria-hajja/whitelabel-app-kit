package com.whitelabel.core.reminders

import kotlinx.datetime.LocalDateTime
import kotlinx.coroutines.flow.Flow

/** Arms and disarms the system alarm that wakes the app for a reminder. */
interface AlarmScheduler {
    fun set(id: String, at: LocalDateTime)
    fun cancel(id: String)
}

/** Shows a reminder as a notification. */
interface NotificationPoster {
    /** Returns false when notifications are blocked; nothing is shown then. */
    fun post(reminder: Reminder): Boolean
}

/** The pending reminders, persisted so they survive the process and reboots. */
interface ReminderStore {
    val reminders: Flow<List<Reminder>>
    suspend fun update(transform: (List<Reminder>) -> List<Reminder>)
}
