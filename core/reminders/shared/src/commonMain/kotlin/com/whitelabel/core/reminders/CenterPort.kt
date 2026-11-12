package com.whitelabel.core.reminders

import com.whitelabel.core.datastore.JsonStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import okio.Path

/** iOS keeps at most this many pending notification requests per app and drops the rest silently. */
const val MAX_PENDING_REQUESTS = 64

/** One notification request handed to the system; [at] null means show now. */
data class CenterRequest(val id: String, val at: LocalDateTime?, val title: String, val text: String, val deepLink: String?)

/** The system's notification center (iOS: `UNUserNotificationCenter`), which shows requests at their time itself. */
interface NotificationCenter {
    /** Adds [request], replacing a pending one with the same id. */
    fun add(request: CenterRequest)
    fun removePending(ids: List<String>)
    fun removeDelivered(ids: List<String>)
}

/** The requests handed to the center: reminder id → the content it was armed with. Persisted, so a later launch knows what iOS already showed. */
interface ArmedStore {
    val armed: Flow<Map<String, String>>
    suspend fun update(transform: (Map<String, String>) -> Map<String, String>)
}

fun ArmedStore(path: Path, scope: CoroutineScope): ArmedStore =
    JsonArmedStore(JsonStore(path, StoredArmed.serializer(), StoredArmed(), scope))

@Serializable
internal data class StoredArmed(val armed: Map<String, String> = emptyMap())

private class JsonArmedStore(private val json: JsonStore<StoredArmed>) : ArmedStore {
    override val armed: Flow<Map<String, String>> = json.data.map { it.armed }
    override suspend fun update(transform: (Map<String, String>) -> Map<String, String>) {
        json.update { it.copy(armed = transform(it.armed)) }
    }
}

/**
 * [ReminderEngine]'s ports for a platform whose notification center shows requests on time without
 * waking the app (iOS). Give the engine [store] (not the raw store) plus this port as both scheduler
 * and poster.
 *
 * - Every store change re-arms the [MAX_PENDING_REQUESTS] soonest future reminders; later ones wait,
 *   unarmed, until a slot frees up.
 * - A reminder whose time has passed **and that was armed** was shown by the system: it's no longer
 *   pending and is removed silently at the next change. One that was **never armed** (beyond the cap)
 *   is still pending, so the engine's late rule decides whether to post it.
 * - [sweepDelivered] then [ReminderEngine.onSystemChange] then `rebalance(rearmAll = true)` once per
 *   launch: the system may have lost a request the record says is armed (a failed add, a migrated
 *   phone), so launch hands every wanted request over again, as Android re-arms every alarm.
 */
class CenterReminderPort(
    private val inner: ReminderStore,
    private val armedStore: ArmedStore,
    private val center: NotificationCenter,
    private val now: () -> LocalDateTime,
) : AlarmScheduler, NotificationPoster {

    private val lock = Mutex()
    /** Ids cancelled since the last rebalance: their request is gone even if [armedStore] still lists them. */
    private val cancelled = MutableStateFlow<Set<String>>(emptySet())

    val store: ReminderStore = object : ReminderStore {
        override val reminders: Flow<List<Reminder>> =
            combine(inner.reminders, armedStore.armed) { list, armed -> list.filterNot { it.wasShown(armed) } }

        override suspend fun update(transform: (List<Reminder>) -> List<Reminder>) = lock.withLock {
            val armed = armedStore.armed.first()
            inner.update { list -> transform(list.filterNot { it.wasShown(armed) }) }
            rebalanceLocked()
        }
    }

    /** Arming follows the store (see [store]); the engine's call needs nothing more. */
    override fun set(id: String, at: LocalDateTime) = Unit

    override fun cancel(id: String) {
        center.removePending(listOf(id))
        center.removeDelivered(listOf(id))
        cancelled.update { it + id }
    }

    override fun post(reminder: Reminder): Boolean {
        center.add(CenterRequest(reminder.id, null, reminder.title, reminder.text, reminder.deepLink))
        return true
    }

    /** Removes the reminders the system already showed. */
    suspend fun sweepDelivered() = store.update { it }

    /** Arms the soonest [MAX_PENDING_REQUESTS] future reminders and disarms the rest; [rearmAll] re-adds even those recorded as armed. */
    suspend fun rebalance(rearmAll: Boolean = false) = lock.withLock { rebalanceLocked(rearmAll) }

    private suspend fun rebalanceLocked(rearmAll: Boolean = false) {
        val list = inner.reminders.first()
        val armed = armedStore.armed.first()
        val justCancelled = cancelled.getAndUpdate { emptySet() }
        val time = now()
        val wanted = list.filter { it.at > time }.sortedWith(ORDER).take(MAX_PENDING_REQUESTS)
        val wantedIds = wanted.mapTo(mutableSetOf()) { it.id }
        // Shown by the system but still stored: keep the record so they're never posted again.
        val shown = list.filter { it.wasShown(armed) }.mapTo(mutableSetOf()) { it.id }

        val disarm = armed.keys.filter { it !in wantedIds && it !in shown }
        if (disarm.isNotEmpty()) center.removePending(disarm)
        wanted.filter { rearmAll || armed[it.id] != it.signature() || it.id in justCancelled }.forEach { center.add(it.toRequest()) }
        val next = wanted.associate { it.id to it.signature() } + armed.filterKeys { it in shown }
        if (next != armed) armedStore.update { next }
    }

    private fun Reminder.wasShown(armed: Map<String, String>) = at <= now() && armed[id] == signature()

    private companion object {
        val ORDER = compareBy<Reminder>({ it.at }, { it.id })
    }
}

private fun Reminder.signature() = "$at|$channel|$title|$text|${deepLink.orEmpty()}"

private fun Reminder.toRequest() = CenterRequest(id, at, title, text, deepLink)
