package com.whitelabel.core.reminders

import android.content.Context
import dagger.BindsOptionalOf
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.Optional
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RemindersModule {
    /** The app's channels, if it binds a [ReminderConfig]. */
    @BindsOptionalOf
    abstract fun config(): ReminderConfig

    companion object {
        @Provides
        @Singleton
        fun reminders(@ApplicationContext context: Context, config: Optional<ReminderConfig>): Reminders {
            ReminderConfigProvider.config = config.orElse(null)
            ensureChannels(context, ReminderConfigProvider.config)
            val runtime = RemindersRuntime.get(context)
            runtime.rearmOnce()
            return Reminders(runtime.engine)
        }
    }
}
