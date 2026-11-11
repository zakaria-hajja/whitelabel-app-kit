package com.whitelabel.core.reminders

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * [Reminders] for an app: add `remindersModule(config)` to its definition. [config] holds the app's
 * notification channels; without it reminders use the default channel.
 */
fun remindersModule(config: ReminderConfig? = null) = module {
    single {
        val context = androidContext()
        ReminderConfigProvider.config = config
        ensureChannels(context, config)
        val runtime = RemindersRuntime.get(context)
        runtime.rearmOnce()
        Reminders(runtime.engine)
    }
}
