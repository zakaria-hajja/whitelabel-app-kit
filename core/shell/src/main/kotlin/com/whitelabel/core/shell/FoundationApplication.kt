package com.whitelabel.core.shell

import android.app.Application
import com.whitelabel.core.analytics.CrashReporter
import javax.inject.Inject

/**
 * Base for every app's Application class, which adds `@HiltAndroidApp` (a Hilt requirement).
 * Uncaught exceptions are recorded with the app's [CrashReporter] before the default handler runs.
 */
open class FoundationApplication : Application() {

    @Inject lateinit var crashReporter: CrashReporter

    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { crashReporter.record(throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
