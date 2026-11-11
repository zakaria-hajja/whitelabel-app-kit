package com.whitelabel.core.reminders

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat

/** The app's notification channels, passed to `remindersModule(config)`; optional. */
data class ReminderConfig(val channels: List<ReminderChannel>)

/** A notification channel; [name] is what the user sees in system settings. */
data class ReminderChannel(
    val id: String,
    @StringRes val name: Int,
    val importance: Int = NotificationManagerCompat.IMPORTANCE_DEFAULT,
)

internal const val DEFAULT_CHANNEL = "reminders"

/** Creates (or renames) the config's channels. Safe to call often; the system keeps the user's choices. */
internal fun ensureChannels(context: Context, config: ReminderConfig?) {
    val manager = NotificationManagerCompat.from(context)
    config?.channels.orEmpty().forEach { channel ->
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(channel.id, channel.importance).setName(context.getString(channel.name)).build(),
        )
    }
}

internal fun ensureDefaultChannel(context: Context) {
    NotificationManagerCompat.from(context).createNotificationChannel(
        NotificationChannelCompat.Builder(DEFAULT_CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(context.getString(R.string.reminders_default_channel))
            .build(),
    )
}

/**
 * Hands the app's config to code that runs outside Koin (the receivers).
 * After a reboot the config may not be pushed yet; the channels created on an earlier run still exist then.
 */
internal object ReminderConfigProvider {
    @Volatile var config: ReminderConfig? = null
}
