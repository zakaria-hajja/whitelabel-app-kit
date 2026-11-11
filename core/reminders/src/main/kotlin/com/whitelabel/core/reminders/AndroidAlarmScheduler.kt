package com.whitelabel.core.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.time.ZoneId
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toJavaLocalDateTime

/** Inexact alarms that still fire in Doze. No exact-alarm permission: Play keeps that for alarm and calendar apps. */
internal class AndroidAlarmScheduler(private val context: Context) : AlarmScheduler {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    override fun set(id: String, at: LocalDateTime) {
        val millis = at.toJavaLocalDateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pendingIntent(id, PendingIntent.FLAG_UPDATE_CURRENT)!!)
    }

    override fun cancel(id: String) {
        val existing = pendingIntent(id, PendingIntent.FLAG_NO_CREATE) ?: return
        alarms.cancel(existing)
        existing.cancel()
    }

    /** The data URI makes each id's intent distinct even when two ids share a hash code. */
    private fun pendingIntent(id: String, flags: Int): PendingIntent? {
        val intent = Intent(context, ReminderAlarmReceiver::class.java)
            .setData(Uri.parse("reminder://" + Uri.encode(id)))
            .putExtra(ReminderAlarmReceiver.EXTRA_ID, id)
        return PendingIntent.getBroadcast(context, id.hashCode(), intent, flags or PendingIntent.FLAG_IMMUTABLE)
    }
}
