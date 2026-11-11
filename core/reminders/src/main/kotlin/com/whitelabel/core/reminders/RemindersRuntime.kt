@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.whitelabel.core.reminders

import android.content.Context
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import okio.Path.Companion.toOkioPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * One engine per process, shared by the app (through Koin) and the receivers (which run without Koin,
 * e.g. right after a reboot). A JsonStore file may only be open once per process, hence the singleton.
 */
internal class RemindersRuntime private constructor(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Not backed up: a restored copy would hold reminders whose alarms were never armed on this phone.
    val store: ReminderStore = ReminderStore(File(context.noBackupFilesDir, "reminders.json").toOkioPath(), scope)

    val engine = ReminderEngine(
        store = store,
        alarms = AndroidAlarmScheduler(context),
        poster = AndroidNotificationPoster(context) { ReminderConfigProvider.config },
        now = { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()) },
    )

    /**
     * Re-arms what's stored, once per process. A force stop (Settings, or an OEM battery manager) removes
     * every alarm and no broadcast follows, so the app's start is the only chance to restore them.
     */
    fun rearmOnce() {
        if (rearmed.compareAndSet(false, true)) scope.launch { runCatching { engine.onSystemChange() } }
    }

    private val rearmed = AtomicBoolean(false)

    companion object {
        @Volatile private var instance: RemindersRuntime? = null

        fun get(context: Context): RemindersRuntime =
            instance ?: synchronized(this) {
                instance ?: RemindersRuntime(context.applicationContext).also { instance = it }
            }
    }
}
