package com.whitelabel.core.reminders

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

/** The persisted form of the pending reminders. Fields have defaults so older files still read. */
@Serializable
internal data class StoredReminders(
    val version: Int = 1,
    val reminders: List<StoredReminder> = emptyList(),
)

@Serializable
internal data class StoredReminder(
    val id: String,
    val at: String,
    val channel: String = "",
    val title: String = "",
    val text: String = "",
    val deepLink: String? = null,
)

internal fun Reminder.toStored() = StoredReminder(id, at.toString(), channel, title, text, deepLink)

/** Null when [at] can't be read; such an entry is dropped rather than crashing the receivers. */
internal fun StoredReminder.toReminderOrNull(): Reminder? =
    runCatching { LocalDateTime.parse(at) }.getOrNull()?.let { Reminder(id, it, channel, title, text, deepLink) }
