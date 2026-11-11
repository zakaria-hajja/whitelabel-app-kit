package com.whitelabel.core.reminders

import com.whitelabel.core.datastore.JsonStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okio.Path

/** The pending reminders persisted as JSON at [path] (keep it out of backups: see `RemindersRuntime`). */
fun ReminderStore(path: Path, scope: CoroutineScope): ReminderStore =
    JsonReminderStore(JsonStore(path, StoredReminders.serializer(), StoredReminders(), scope))

private class JsonReminderStore(private val json: JsonStore<StoredReminders>) : ReminderStore {
    override val reminders: Flow<List<Reminder>> =
        json.data.map { doc -> doc.reminders.mapNotNull { it.toReminderOrNull() } }

    override suspend fun update(transform: (List<Reminder>) -> List<Reminder>) {
        json.update { doc ->
            doc.copy(reminders = transform(doc.reminders.mapNotNull { it.toReminderOrNull() }).map { it.toStored() })
        }
    }
}
