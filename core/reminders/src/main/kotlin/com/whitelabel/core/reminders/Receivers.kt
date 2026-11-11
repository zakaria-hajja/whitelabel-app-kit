package com.whitelabel.core.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** A reminder's alarm went off. */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val engine = RemindersRuntime.get(context).engine
        runAsync { engine.onAlarm(id) }
    }

    internal companion object {
        const val EXTRA_ID = "com.whitelabel.core.reminders.ID"
    }
}

/** Boot, a clock or time-zone change, or an app update: every pending reminder is re-armed. */
class ReminderSystemReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val engine = RemindersRuntime.get(context).engine
        runAsync { engine.onSystemChange() }
    }

    private companion object {
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}

/**
 * Runs [block] off the main thread within the broadcast's time; inline when called directly (no pending
 * result). A failure is logged, never thrown: an uncaught one would crash the app in the background on every boot.
 */
private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    if (pending == null) {
        runBlocking { logFailure { block() } }
        return
    }
    CoroutineScope(Dispatchers.Default).launch {
        try {
            logFailure { block() }
        } finally {
            pending.finish()
        }
    }
}

private suspend fun logFailure(block: suspend () -> Unit) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("Reminders", "A reminder broadcast failed", e)
    }
}
