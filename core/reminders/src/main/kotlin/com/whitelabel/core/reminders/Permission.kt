package com.whitelabel.core.reminders

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/** Whether reminders can show, and what the app can do about it. */
enum class NotificationPermission {
    Granted,

    /** Android 13+, never asked: [NotificationPermissionState.request] shows the system dialog. */
    NotAskedYet,

    /** Refused once: the system dialog can be shown one more time. */
    Denied,

    /** Only system settings can turn notifications on: use [openNotificationSettings]. */
    BlockedInSettings,
}

internal fun permissionState(enabled: Boolean, sdk: Int, askedBefore: Boolean, showRationale: Boolean): NotificationPermission = when {
    enabled -> NotificationPermission.Granted
    sdk < 33 -> NotificationPermission.BlockedInSettings
    !askedBefore -> NotificationPermission.NotAskedYet
    showRationale -> NotificationPermission.Denied
    else -> NotificationPermission.BlockedInSettings
}

/** The notification permission's current [state]; re-read whenever the screen resumes (e.g. back from settings). */
@Stable
class NotificationPermissionState internal constructor(initial: NotificationPermission) {
    var state: NotificationPermission by mutableStateOf(initial)
        internal set

    internal var onRequest: () -> Unit = {}

    /** Shows the system dialog when the state is [NotificationPermission.NotAskedYet] or [NotificationPermission.Denied]; otherwise does nothing. */
    fun request() = onRequest()
}

/** The app shows its own rationale first, then calls [NotificationPermissionState.request]. The module never prompts by itself. */
@Composable
fun rememberNotificationPermission(): NotificationPermissionState {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    fun read() = permissionState(
        enabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        sdk = Build.VERSION.SDK_INT,
        askedBefore = prefs.getBoolean(KEY_ASKED, false),
        showRationale = Build.VERSION.SDK_INT >= 33 &&
            context.findActivity()?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.POST_NOTIFICATIONS) } == true,
    )

    val holder = remember(context) { NotificationPermissionState(read()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { holder.state = read() }
    holder.onRequest = {
        val current = holder.state
        if (Build.VERSION.SDK_INT >= 33 &&
            (current == NotificationPermission.NotAskedYet || current == NotificationPermission.Denied)
        ) {
            prefs.edit { putBoolean(KEY_ASKED, true) }
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { holder.state = read() }
    return holder
}

/** Opens this app's notification settings, where a blocked permission (or a muted channel) is turned back on. */
fun Context.openNotificationSettings() {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}

private const val PREFS = "reminders"
private const val KEY_ASKED = "notification_permission_asked"

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
