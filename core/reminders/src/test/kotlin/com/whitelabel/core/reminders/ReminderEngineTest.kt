package com.whitelabel.core.reminders

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import java.time.LocalDateTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ReminderEngineTest {

    private val now = LocalDateTime.of(2027, 3, 10, 8, 0)
    private val store = MemoryStore()
    private val alarms = FakeAlarms()
    private val poster = FakePoster()
    private val engine = ReminderEngine(store, alarms, poster) { now }

    private fun reminder(id: String, at: LocalDateTime) =
        Reminder(id = id, at = at, channel = "c", title = "t $id", text = "x", deepLink = null)

    private fun at(hour: Int) = LocalDateTime.of(2027, 3, 10, hour, 0)

    @Test fun `schedule stores and arms`() = runTest {
        engine.schedule(reminder("a", at(9)))

        assertThat(engine.pending()).containsExactly(reminder("a", at(9)))
        assertThat(alarms.log).containsExactly("set a ${at(9)}")
    }

    @Test fun `schedule with the same id replaces and cancels the old alarm`() = runTest {
        engine.schedule(reminder("a", at(9)))
        engine.schedule(reminder("a", at(10)))

        assertThat(engine.pending()).containsExactly(reminder("a", at(10)))
        assertThat(alarms.log).containsExactly("set a ${at(9)}", "cancel a", "set a ${at(10)}")
    }

    @Test fun `setAll arms the new set and cancels the rest of the prefix only`() = runTest {
        engine.schedule(reminder("m:a", at(9)))
        engine.schedule(reminder("m:b", at(9)))
        engine.schedule(reminder("x:z", at(9)))
        alarms.log.clear()

        engine.setAll(listOf(reminder("m:a", at(9)), reminder("m:c", at(11))), idPrefix = "m:")

        assertThat(engine.pending().map { it.id }).containsExactly("m:a", "x:z", "m:c")
        assertThat(alarms.log).containsExactly("cancel m:b", "set m:c ${at(11)}")
    }

    @Test fun `setAll re-arms a changed reminder`() = runTest {
        engine.schedule(reminder("m:a", at(9)))
        alarms.log.clear()

        engine.setAll(listOf(reminder("m:a", at(10))), idPrefix = "m:")

        assertThat(engine.pending()).containsExactly(reminder("m:a", at(10)))
        assertThat(alarms.log).containsExactly("cancel m:a", "set m:a ${at(10)}")
    }

    @Test fun `cancel removes one and cancels its alarm`() = runTest {
        engine.schedule(reminder("a", at(9)))
        engine.schedule(reminder("b", at(10)))
        alarms.log.clear()

        engine.cancel("a")

        assertThat(engine.pending().map { it.id }).containsExactly("b")
        assertThat(alarms.log).containsExactly("cancel a")
    }

    @Test fun `cancelAll removes every reminder and cancels every alarm`() = runTest {
        engine.schedule(reminder("a", at(9)))
        engine.schedule(reminder("b", at(10)))
        alarms.log.clear()

        engine.cancelAll()

        assertThat(engine.pending()).isEmpty()
        assertThat(alarms.log).containsExactly("cancel a", "cancel b")
    }

    @Test fun `onAlarm posts a stored due reminder and removes it`() = runTest {
        store.seed(reminder("a", now))

        engine.onAlarm("a")

        assertThat(poster.posted.map { it.id }).containsExactly("a")
        assertThat(engine.pending()).isEmpty()
    }

    @Test fun `onAlarm for an unknown id posts nothing`() = runTest {
        engine.onAlarm("gone")

        assertThat(poster.posted).isEmpty()
        assertThat(alarms.log).isEmpty()
    }

    @Test fun `onAlarm when notifications are blocked removes it without posting`() = runTest {
        poster.blocked = true
        store.seed(reminder("a", now))

        engine.onAlarm("a")

        assertThat(poster.posted).isEmpty()
        assertThat(engine.pending()).isEmpty()
    }

    @Test fun `onAlarm before its time re-arms instead of posting`() = runTest {
        store.seed(reminder("a", at(9)))

        engine.onAlarm("a")

        assertThat(poster.posted).isEmpty()
        assertThat(engine.pending()).containsExactly(reminder("a", at(9)))
        assertThat(alarms.log).containsExactly("set a ${at(9)}")
    }

    @Test fun `onSystemChange re-arms future reminders, fires recent ones, drops old ones`() = runTest {
        val recent = LocalDateTime.of(2027, 3, 9, 20, 1)
        val old = LocalDateTime.of(2027, 3, 9, 19, 59)
        store.seed(reminder("future", at(9)), reminder("recent", recent), reminder("old", old))

        engine.onSystemChange()

        assertThat(alarms.log).containsExactly("set future ${at(9)}")
        assertThat(poster.posted.map { it.id }).containsExactly("recent")
        assertThat(engine.pending().map { it.id }).containsExactly("future")
    }

    @Test fun `scheduling a past time follows the same late rule`() = runTest {
        engine.schedule(reminder("recent", LocalDateTime.of(2027, 3, 9, 20, 1)))
        engine.schedule(reminder("old", LocalDateTime.of(2027, 3, 9, 19, 59)))

        assertThat(poster.posted.map { it.id }).containsExactly("recent")
        assertThat(engine.pending()).isEmpty()
        assertThat(alarms.log.filter { it.startsWith("set") }).isEmpty()
    }

    @Test fun `a cancelled caller still leaves the reminder armed`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val slowStore = MemoryStore(afterWrite = { gate.await() })
        val engine = ReminderEngine(slowStore, alarms, poster) { now }

        val job = launch { engine.schedule(reminder("a", at(9))) }
        testScheduler.advanceUntilIdle()
        job.cancel()
        gate.complete(Unit)
        job.join()

        assertThat(slowStore.reminders.first()).containsExactly(reminder("a", at(9)))
        assertThat(alarms.log).containsExactly("set a ${at(9)}")
    }

    @Test fun `onSystemChange keeps re-arming after one alarm fails`() = runTest {
        store.seed(reminder("a", at(9)), reminder("b", at(10)))
        alarms.failFor = "a"

        engine.onSystemChange()

        assertThat(alarms.log).containsExactly("set b ${at(10)}")
    }

    @Test fun `setAll with a past time follows the same late rule`() = runTest {
        engine.setAll(
            listOf(reminder("m:recent", LocalDateTime.of(2027, 3, 9, 20, 1)), reminder("m:old", LocalDateTime.of(2027, 3, 9, 19, 59))),
            idPrefix = "m:",
        )

        assertThat(poster.posted.map { it.id }).containsExactly("m:recent")
        assertThat(engine.pending()).isEmpty()
    }
}

private class MemoryStore(private val afterWrite: suspend () -> Unit = {}) : ReminderStore {
    private val state = MutableStateFlow<List<Reminder>>(emptyList())
    override val reminders: Flow<List<Reminder>> = state
    override suspend fun update(transform: (List<Reminder>) -> List<Reminder>) {
        state.value = transform(state.value)
        afterWrite() // like DataStore: the write is committed, then the caller may see a cancellation
    }
    fun seed(vararg items: Reminder) {
        state.value = items.toList()
    }
}

private class FakeAlarms : AlarmScheduler {
    val log = mutableListOf<String>()
    var failFor: String? = null
    override fun set(id: String, at: LocalDateTime) {
        if (id == failFor) throw IllegalStateException("alarm limit")
        log += "set $id $at"
    }
    override fun cancel(id: String) { log += "cancel $id" }
}

private class FakePoster : NotificationPoster {
    var blocked = false
    val posted = mutableListOf<Reminder>()
    override fun post(reminder: Reminder): Boolean {
        if (blocked) return false
        posted += reminder
        return true
    }
}
