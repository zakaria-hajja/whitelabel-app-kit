package com.whitelabel.core.reminders

import kotlin.time.Duration.Companion.hours
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
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
    suspend fun schedule(reminder: Reminder) = locked {
        val existing = current().find { it.id == reminder.id }
        if (existing != null) alarms.cancel(existing.id)
        place(reminder)
    }

    /**
     * Makes the stored reminders whose id starts with [idPrefix] exactly [reminders]:
     * new and changed ones are armed, unchanged ones are left alone, the rest under the prefix are cancelled.
     *
     * A wanted reminder whose time has passed follows the late rule (posted if recent) when [postPast] is
     * true, the default. With [postPast] false, `setAll` never posts, and reads the time once:
     * - a wanted reminder whose time has passed and whose stored copy is still due (its time has passed
     *   too) is left exactly as stored, even if the wanted copy differs: it is the only copy still owed,
     *   and replacing it would mean posting it here or losing it. Its alarm shows it once (on iOS, one
     *   past the armed cap waits for the next launch's [onSystemChange]);
     * - one whose stored copy isn't due yet is cancelled and removed: the plan no longer wants it then;
     * - one that isn't stored was shown already or missed, so it is dropped;
     * - a future one is stored and armed, even if the clock passes its time while `setAll` runs (the
     *   alarm then fires at once).
     * Use it when the list was computed from a snapshot that may be stale by the time this runs (say a
     * replan racing a reminder's alarm): an alarm that fired in between can't make the reminder show twice.
     * In both modes a stored reminder left out of [reminders] is cancelled and never shown.
     */
    suspend fun setAll(reminders: List<Reminder>, idPrefix: String, postPast: Boolean = true) = locked {
        require(reminders.all { it.id.startsWith(idPrefix) }) { "Every reminder id must start with \"$idPrefix\"" }
        val wanted = reminders.associateBy { it.id }
        val stored = current().filter { it.id.startsWith(idPrefix) }
        val gone = stored.filter { it.id !in wanted }
        gone.forEach { alarms.cancel(it.id) }
        val goneIds = gone.map { it.id }.toSet()
        if (goneIds.isNotEmpty()) store.update { list -> list.filterNot { it.id in goneIds } }

        val storedById = stored.associateBy { it.id }
        val time = now()
        wanted.values.forEach { reminder ->
            val old = storedById[reminder.id]
            if (old == reminder) return@forEach
            if (postPast) {
                if (old != null) alarms.cancel(old.id)
                place(reminder)
            } else if (reminder.at > time) {
                if (old != null) alarms.cancel(old.id)
                storeAndArm(reminder)
            } else if (old != null && old.at > time) {
                // Not due yet, and the plan moved it into the past: it no longer happens then.
                alarms.cancel(old.id)
                store.update { list -> list.filterNot { it.id == old.id } }
            }
            // Otherwise: stored and due (its alarm owns it) or not stored (shown or missed): leave it.
        }
    }

    suspend fun cancel(id: String) = locked {
        if (current().any { it.id == id }) {
            alarms.cancel(id)
            store.update { list -> list.filterNot { it.id == id } }
        }
    }

    suspend fun cancelAll() = locked {
        current().forEach { alarms.cancel(it.id) }
        store.update { emptyList() }
    }

    /** The alarm for [id] went off: show it if it's still pending and due, then forget it. */
    suspend fun onAlarm(id: String) = locked {
        val reminder = current().find { it.id == id } ?: return@locked
        if (reminder.at > now()) {
            alarms.set(reminder.id, reminder.at) // woke early (e.g. the clock moved): wait for the real time
        } else {
            fire(reminder)
        }
    }

    /** After a reboot, a clock or time-zone change, or an app update: alarms are gone or wrong, so rebuild them. */
    suspend fun onSystemChange() = locked {
        val time = now()
        current().forEach { reminder ->
            // One failure (e.g. the system's alarm limit) must not leave the rest unarmed.
            runCatching { if (reminder.at > time) alarms.set(reminder.id, reminder.at) else fire(reminder) }
                .onFailure { if (it is CancellationException) throw it }
        }
    }

    suspend fun pending(): List<Reminder> = current()

    /**
     * Serialized and not cancellable: a store write and its alarm happen together, even when the caller
     * (say a ViewModel scope) is cancelled halfway, so no reminder is ever stored without its alarm.
     */
    private suspend fun <T> locked(block: suspend () -> T): T = withContext(NonCancellable) { lock.withLock { block() } }

    private suspend fun current(): List<Reminder> = store.reminders.first()

    /** Stores and arms a future reminder; a past one is removed and posted if it's recent enough. */
    private suspend fun place(reminder: Reminder) {
        if (reminder.at > now()) storeAndArm(reminder) else fire(reminder)
    }

    private suspend fun storeAndArm(reminder: Reminder) {
        store.update { list -> (list.filterNot { it.id == reminder.id } + reminder).sortedWith(ORDER) }
        alarms.set(reminder.id, reminder.at)
    }

    /** Removes [reminder] first (so it is never shown twice), then posts it unless it's too late. */
    private suspend fun fire(reminder: Reminder) {
        store.update { list -> list.filterNot { it.id == reminder.id } }
        // Like java.time's Duration.between on local times: wall-clock difference, no time zone.
        val late = now().toInstant(TimeZone.UTC) - reminder.at.toInstant(TimeZone.UTC)
        if (late < LATE_LIMIT_HOURS.hours) poster.post(reminder)
    }

    private companion object {
        val ORDER = compareBy<Reminder>({ it.at }, { it.id })
    }
}
