package com.whitelabel.core.reminders

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test
import kotlinx.datetime.LocalDateTime

/** Reminders stored by the java.time version (LocalDateTime.toString()) must still fire after the move. */
class StoredReminderCompatTest {

    private fun stored(at: String) = StoredReminder(id = "r", at = at)

    @Test fun `reads what java time wrote`() {
        assertThat(stored("2026-11-11T09:00").toReminderOrNull()?.at).isEqualTo(LocalDateTime(2026, 11, 11, 9, 0))
        assertThat(stored("2026-11-11T09:00:30").toReminderOrNull()?.at).isEqualTo(LocalDateTime(2026, 11, 11, 9, 0, 30))
        assertThat(stored("2026-11-11T09:00:30.123456789").toReminderOrNull()?.at)
            .isEqualTo(LocalDateTime(2026, 11, 11, 9, 0, 30, 123_456_789))
    }

    @Test fun `writes what java time wrote`() {
        val reminder = Reminder("r", LocalDateTime(2026, 11, 11, 9, 0), "c", "t", "x", null)
        assertThat(reminder.toStored().at).isEqualTo("2026-11-11T09:00")
    }

    @Test fun `an unreadable time is dropped instead of crashing`() {
        assertThat(stored("next tuesday").toReminderOrNull()).isNull()
    }
}
