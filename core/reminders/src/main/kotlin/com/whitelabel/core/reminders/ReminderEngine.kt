package com.whitelabel.core.reminders

import java.time.Duration
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A reminder this late (or less) is still shown; anything older is dropped. */
internal const val LATE_LIMIT_HOURS = 12L

/**
 * Every reminder decision, with no Android code: what to store, arm, cancel, post and drop.
 * Operations are serialized, so a receiver and the app never interleave.
 */
class ReminderEngine(
    private val store: ReminderStore,
    private val alarms: AlarmScheduler,
    private val poster: NotificationPoster,
    private val now: () -> LocalDateTime,
) {
    private val lock = Mutex()

    /** Stores and arms [reminder], replacing (and disarming) one with the same id. A past time follows the late rule. */
    suspend fun schedule(reminder: Reminder) = lock.withLock {
        val existing = current().find { it.id == reminder.id }
        if (existing != null) alarms.cancel(existing.id)
        place(reminder)
    }

    /**
     * Makes the stored reminders whose id starts with [idPrefix] exactly [reminders]:
     * new and changed ones are armed, unchanged ones are left alone, the rest under the prefix are cancelled.
     */
    suspend fun setAll(reminders: List<Reminder>, idPrefix: String) = lock.withLock {
        require(reminders.all { it.id.startsWith(idPrefix) }) { "Every reminder id must start with \"$idPrefix\"" }
        val wanted = reminders.associateBy { it.id }
        val stored = current().filter { it.id.startsWith(idPrefix) }
        val gone = stored.filter { it.id !in wanted }
        gone.forEach { alarms.cancel(it.id) }
        val goneIds = gone.map { it.id }.toSet()
        if (goneIds.isNotEmpty()) store.update { list -> list.filterNot { it.id in goneIds } }

        val storedById = stored.associateBy { it.id }
        wanted.values.forEach { reminder ->
            val old = storedById[reminder.id]
            if (old == reminder) return@forEach
            if (old != null) alarms.cancel(old.id)
            place(reminder)
        }
    }

    suspend fun cancel(id: String) = lock.withLock {
        if (current().any { it.id == id }) {
            alarms.cancel(id)
            store.update { list -> list.filterNot { it.id == id } }
        }
    }

    suspend fun cancelAll() = lock.withLock {
        current().forEach { alarms.cancel(it.id) }
        store.update { emptyList() }
    }

    /** The alarm for [id] went off: show it if it's still pending and due, then forget it. */
    suspend fun onAlarm(id: String) = lock.withLock {
        val reminder = current().find { it.id == id } ?: return@withLock
        if (reminder.at.isAfter(now())) {
            alarms.set(reminder.id, reminder.at) // woke early (e.g. the clock moved): wait for the real time
        } else {
            fire(reminder)
        }
    }

    /** After a reboot, a clock or time-zone change, or an app update: alarms are gone or wrong, so rebuild them. */
    suspend fun onSystemChange() = lock.withLock {
        val time = now()
        current().forEach { reminder ->
            if (reminder.at.isAfter(time)) alarms.set(reminder.id, reminder.at) else fire(reminder)
        }
    }

    suspend fun pending(): List<Reminder> = current()

    private suspend fun current(): List<Reminder> = store.reminders.first()

    /** Stores and arms a future reminder; a past one is removed and posted if it's recent enough. */
    private suspend fun place(reminder: Reminder) {
        if (reminder.at.isAfter(now())) {
            store.update { list -> (list.filterNot { it.id == reminder.id } + reminder).sortedWith(ORDER) }
            alarms.set(reminder.id, reminder.at)
        } else {
            fire(reminder)
        }
    }

    /** Removes [reminder] first (so it is never shown twice), then posts it unless it's too late. */
    private suspend fun fire(reminder: Reminder) {
        store.update { list -> list.filterNot { it.id == reminder.id } }
        val late = Duration.between(reminder.at, now())
        if (late < Duration.ofHours(LATE_LIMIT_HOURS)) poster.post(reminder)
    }

    private companion object {
        val ORDER = compareBy<Reminder>({ it.at }, { it.id })
    }
}
