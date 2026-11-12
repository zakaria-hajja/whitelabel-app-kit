package com.whitelabel.core.reminders

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.single
import kotlin.test.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

class CenterReminderPortTest {

    private val start = LocalDateTime(2027, 3, 10, 8, 0)
    private var now = start
    private val center = FakeCenter()
    private val reminderStore = PortMemoryStore()
    private val armedStore = MemoryArmedStore()
    private val port = CenterReminderPort(reminderStore, armedStore, center) { now }
    private val engine = ReminderEngine(port.store, port, port) { now }

    private fun plus(at: LocalDateTime, minutes: Int) =
        (at.toInstant(TimeZone.UTC) + minutes.minutes).toLocalDateTime(TimeZone.UTC)

    private fun reminder(id: String, at: LocalDateTime, title: String = "T") =
        Reminder(id = id, at = at, channel = "c", title = title, text = "x", deepLink = "app://$id")

    /** r1 … rN, one minute apart from 09:00, scheduled latest first. */
    private suspend fun scheduleMany(count: Int): List<Reminder> {
        val all = (1..count).map { reminder("r$it", plus(LocalDateTime(2027, 3, 10, 9, 0), it)) }
        all.reversed().forEach { engine.schedule(it) }
        return all
    }

    @Test fun schedule_armsTheRequestWithTheReminderContent() = runTest {
        val at = LocalDateTime(2027, 3, 10, 9, 0)
        engine.schedule(reminder("a", at))

        assertThat(center.added).single().isEqualTo(CenterRequest("a", at, "T", "x", "app://a"))
        assertThat(center.pending.keys.toList()).containsExactly("a")
    }

    @Test fun cap_armsOnlyThe64Soonest() = runTest {
        val all = scheduleMany(70)

        assertThat(center.pending.keys).containsExactlyInAnyOrder(*all.take(MAX_PENDING_REQUESTS).map { it.id }.toTypedArray())
        assertThat(center.pending.keys.filter { it in setOf("r65", "r66", "r67", "r68", "r69", "r70") }).isEmpty()
        assertThat(engine.pending()).isEqualTo(all)
    }

    @Test fun cancel_freesASlot_armsTheNextSoonest() = runTest {
        scheduleMany(70)

        engine.cancel("r3")

        assertThat(center.pending.keys.contains("r3")).isEqualTo(false)
        assertThat(center.pending.keys.contains("r65")).isEqualTo(true)
        assertThat(center.pending.size).isEqualTo(MAX_PENDING_REQUESTS)
    }

    @Test fun replacingWithTheSameContent_staysArmed() = runTest {
        val at = LocalDateTime(2027, 3, 10, 9, 0)
        engine.schedule(reminder("a", at))
        engine.schedule(reminder("a", at)) // the engine cancels, then places the same reminder

        assertThat(center.pending.keys.toList()).containsExactly("a")
    }

    @Test fun changedContent_isReArmed() = runTest {
        val at = LocalDateTime(2027, 3, 10, 9, 0)
        engine.setAll(listOf(reminder("m:a", at)), idPrefix = "m:")
        engine.setAll(listOf(reminder("m:a", at, title = "New")), idPrefix = "m:")

        assertThat(center.pending.getValue("m:a").title).isEqualTo("New")
    }

    @Test fun pastAndArmed_isSweptSilently() = runTest {
        engine.schedule(reminder("a", LocalDateTime(2027, 3, 10, 9, 0)))
        center.deliverDue(LocalDateTime(2027, 3, 10, 10, 0))
        now = LocalDateTime(2027, 3, 10, 10, 0)

        port.sweepDelivered()
        engine.onSystemChange()

        assertThat(engine.pending()).isEmpty()
        assertThat(center.added.filter { it.at == null }).isEmpty()
    }

    @Test fun pastAndArmed_isNotPendingBeforeTheSweep() = runTest {
        engine.schedule(reminder("a", LocalDateTime(2027, 3, 10, 9, 0)))
        now = LocalDateTime(2027, 3, 10, 10, 0)

        assertThat(engine.pending()).isEmpty()
    }

    @Test fun pastNeverArmed_followsTheLateRule() = runTest {
        val all = scheduleMany(70)
        now = all[65].at // r65 and r66 are due, never armed; r67 … r70 are still ahead

        port.sweepDelivered()
        engine.onSystemChange()
        port.rebalance()

        assertThat(center.added.filter { it.at == null }.map { it.id }).containsExactly("r65", "r66")
        assertThat(engine.pending().map { it.id }).containsExactly("r67", "r68", "r69", "r70")
        assertThat(center.pending.keys).containsExactlyInAnyOrder("r67", "r68", "r69", "r70")
    }

    @Test fun pastNeverArmed_tooLate_isDropped() = runTest {
        val all = scheduleMany(65)
        now = (all[64].at.toInstant(TimeZone.UTC) + 13.hours).toLocalDateTime(TimeZone.UTC)

        port.sweepDelivered()
        engine.onSystemChange()

        assertThat(center.added.filter { it.at == null }).isEmpty()
        assertThat(engine.pending()).isEmpty()
    }

    @Test fun setAll_unchangedReminders_areNotReAdded() = runTest {
        val list = listOf(reminder("m:a", LocalDateTime(2027, 3, 10, 9, 0)), reminder("m:b", LocalDateTime(2027, 3, 10, 9, 5)))
        engine.setAll(list, idPrefix = "m:")
        val added = center.added.size

        engine.setAll(list, idPrefix = "m:")

        assertThat(center.added.size).isEqualTo(added)
    }

    /**
     * The record says armed but iOS no longer holds the request (a failed add, a migrated phone):
     * the next launch must arm it again, as Android re-arms every alarm, or it would pass as "shown".
     */
    @Test fun launch_reArmsWhatTheRecordSaysIsArmed() = runTest {
        engine.schedule(reminder("a", LocalDateTime(2027, 3, 10, 9, 0)))
        center.pending.clear()

        val relaunched = CenterReminderPort(reminderStore, armedStore, center) { now }
        relaunched.sweepDelivered()
        ReminderEngine(relaunched.store, relaunched, relaunched) { now }.onSystemChange()
        relaunched.rebalance(rearmAll = true)

        assertThat(center.pending.keys.toList()).containsExactly("a")
    }

    @Test fun cancel_removesTheDeliveredNotificationToo() = runTest {
        engine.schedule(reminder("a", LocalDateTime(2027, 3, 10, 9, 0)))

        engine.cancel("a")

        assertThat(center.removedDelivered).containsExactly("a")
        assertThat(center.pending.keys).isEmpty()
    }
}

private class FakeCenter : NotificationCenter {
    val added = mutableListOf<CenterRequest>()
    val pending = linkedMapOf<String, CenterRequest>()
    val removedDelivered = mutableListOf<String>()

    override fun add(request: CenterRequest) {
        added += request
        if (request.at != null) pending[request.id] = request
    }

    override fun removePending(ids: List<String>) {
        ids.forEach { pending.remove(it) }
    }

    override fun removeDelivered(ids: List<String>) {
        ids.forEach { pending.remove(it) }
        removedDelivered += ids
    }

    /** iOS shows every request due by [time] without running the app. */
    fun deliverDue(time: LocalDateTime) {
        pending.values.filter { it.at!! <= time }.map { it.id }.forEach { pending.remove(it) }
    }
}

private class PortMemoryStore : ReminderStore {
    private val state = MutableStateFlow<List<Reminder>>(emptyList())
    override val reminders: Flow<List<Reminder>> = state
    override suspend fun update(transform: (List<Reminder>) -> List<Reminder>) {
        state.value = transform(state.value)
    }
}

private class MemoryArmedStore : ArmedStore {
    private val state = MutableStateFlow<Map<String, String>>(emptyMap())
    override val armed: Flow<Map<String, String>> = state
    override suspend fun update(transform: (Map<String, String>) -> Map<String, String>) {
        state.value = transform(state.value)
    }
}
