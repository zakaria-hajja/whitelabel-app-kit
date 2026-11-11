package com.whitelabel.core.reminders

import android.Manifest
import android.app.ActivityOptions
import android.app.Instrumentation
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RemindersDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val runtime = RemindersRuntime.get(context)

    @Before fun setUp() = runBlocking {
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        ReminderConfigProvider.config = ReminderConfig(listOf(ReminderChannel("renewals", R.string.reminders_default_channel)))
        runtime.engine.cancelAll()
        notifications.cancelAll()
    }

    @After fun tearDown() = runBlocking {
        runtime.engine.cancelAll()
        notifications.cancelAll()
    }

    private fun reminder(id: String, at: LocalDateTime, title: String = "Spotify is hungry today") =
        Reminder(id = id, at = at, channel = "renewals", title = title, text = "€10.99 today", deepLink = "test://reminder/$id")

    private fun posted(title: String): Notification? =
        notifications.activeNotifications.map { it.notification }
            .firstOrNull { it.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() == title }

    private fun waitFor(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (condition()) return true
            Thread.sleep(200)
        }
        return condition()
    }

    @Test fun aReminderFiresAndPosts() = runBlocking {
        Reminders(runtime.engine).schedule(reminder("t1", LocalDateTime.now().plusSeconds(3)))

        assertThat(waitFor(15_000) { posted("Spotify is hungry today") != null }).isEqualTo(true)
        assertThat(waitFor(2_000) { runBlocking { runtime.engine.pending().none { it.id == "t1" } } }).isEqualTo(true)
    }

    @Test fun systemChangeFiresARecentMissedReminderAndDropsAnOldOne() = runBlocking {
        val now = LocalDateTime.now()
        runtime.store.update { listOf(reminder("late", now.minusHours(1), "Late one"), reminder("old", now.minusHours(13), "Old one")) }

        ReminderSystemReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        assertThat(posted("Late one")).isNotNull()
        assertThat(posted("Old one")).isNull()
        assertThat(runtime.engine.pending()).isEmpty()
    }

    @Test fun theTapOpensTheDeepLink() = runBlocking {
        runtime.store.update { listOf(reminder("t1", LocalDateTime.now().minusMinutes(1))) }
        ReminderAlarmReceiver().onReceive(context, Intent().putExtra(ReminderAlarmReceiver.EXTRA_ID, "t1"))
        val notification = posted("Spotify is hungry today")
        assertThat(notification).isNotNull()

        val filter = IntentFilter(Intent.ACTION_VIEW).apply { addDataScheme("test") }
        val monitor = Instrumentation.ActivityMonitor(filter, null, false)
        instrumentation.addMonitor(monitor)
        try {
            val options = if (Build.VERSION.SDK_INT >= 34) {
                ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    .toBundle()
            } else {
                null
            }
            notification!!.contentIntent.send(context, 0, null, null, null, null, options)
            val opened = instrumentation.waitForMonitorWithTimeout(monitor, 10_000)
            assertThat(opened).isNotNull()
            assertThat(opened.intent.dataString).isEqualTo("test://reminder/t1")
            assertThat(opened.intent.action).isEqualTo(Intent.ACTION_VIEW)
            opened.finish()
        } finally {
            instrumentation.removeMonitor(monitor)
        }
        assertThat(runtime.engine.pending().map { it.id }).containsExactly()
    }
}
