package com.whitelabel.core.reminders

import android.content.Context
import com.whitelabel.core.datastore.JsonStore
import com.whitelabel.core.datastore.jsonStoreFile
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * One engine per process, shared by the app (through Hilt) and the receivers (which run without Hilt,
 * e.g. right after a reboot). A JsonStore file may only be open once per process, hence the singleton.
 */
internal class RemindersRuntime private constructor(context: Context) {
    val store: ReminderStore = JsonReminderStore(
        JsonStore(
            file = context.jsonStoreFile("reminders"),
            serializer = StoredReminders.serializer(),
            default = StoredReminders(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        ),
    )

    val engine = ReminderEngine(
        store = store,
        alarms = AndroidAlarmScheduler(context),
        poster = AndroidNotificationPoster(context) { ReminderConfigProvider.config },
        now = LocalDateTime::now,
    )

    companion object {
        @Volatile private var instance: RemindersRuntime? = null

        fun get(context: Context): RemindersRuntime =
            instance ?: synchronized(this) {
                instance ?: RemindersRuntime(context.applicationContext).also { instance = it }
            }
    }
}

private class JsonReminderStore(private val json: JsonStore<StoredReminders>) : ReminderStore {
    override val reminders: Flow<List<Reminder>> =
        json.data.map { doc -> doc.reminders.mapNotNull { it.toReminderOrNull() } }

    override suspend fun update(transform: (List<Reminder>) -> List<Reminder>) {
        json.update { doc ->
            doc.copy(reminders = transform(doc.reminders.mapNotNull { it.toReminderOrNull() }).map { it.toStored() })
        }
    }
}
