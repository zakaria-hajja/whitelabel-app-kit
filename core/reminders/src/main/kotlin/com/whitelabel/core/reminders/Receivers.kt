package com.whitelabel.core.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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

/** Runs [block] off the main thread within the broadcast's time; inline when called directly (no pending result). */
private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    if (pending == null) {
        runBlocking { block() }
        return
    }
    CoroutineScope(Dispatchers.Default).launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}
