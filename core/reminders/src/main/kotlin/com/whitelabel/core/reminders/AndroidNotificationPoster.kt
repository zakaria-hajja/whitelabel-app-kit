package com.whitelabel.core.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Posts the app's text as is: no grouping, throttling or rewording. */
internal class AndroidNotificationPoster(
    private val context: Context,
    private val config: () -> ReminderConfig?,
) : NotificationPoster {
    private val manager = NotificationManagerCompat.from(context)

    @SuppressLint("MissingPermission") // checked by canPost()
    override fun post(reminder: Reminder): Boolean {
        val channel = channelFor(reminder.channel)
        if (!canPost(channel)) return false
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.brand_notification_icon)
            .setContentTitle(reminder.title)
            .setContentText(reminder.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.text))
            .setAutoCancel(true)
            .setContentIntent(contentIntent(reminder))
            .build()
        manager.notify(reminder.id, 0, notification)
        return true
    }

    /** The reminder's channel if the app declared it (now or on an earlier run), else the default one. */
    private fun channelFor(id: String): String {
        if (manager.getNotificationChannelCompat(id) != null) return id
        ensureChannels(context, config())
        if (manager.getNotificationChannelCompat(id) != null) return id
        ensureDefaultChannel(context)
        return DEFAULT_CHANNEL
    }

    private fun canPost(channel: String): Boolean {
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return manager.getNotificationChannelCompat(channel)?.importance != NotificationManagerCompat.IMPORTANCE_NONE
    }

    private fun contentIntent(reminder: Reminder): PendingIntent? {
        val intent = reminder.deepLink
            ?.let { Intent(Intent.ACTION_VIEW, Uri.parse(it)).setPackage(context.packageName) }
            ?: context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return null
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            reminder.id.hashCode(),
            intent.withIdentifier(reminder.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

/** Keeps each reminder's content intent distinct; Intent.setIdentifier is API 29+. */
private fun Intent.withIdentifier(id: String): Intent =
    if (Build.VERSION.SDK_INT >= 29) setIdentifier(id) else this
