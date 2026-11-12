package com.whitelabel.core.reminders

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSNumber
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUUID

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class SystemNotificationCenterIosTest {

    @Test fun trigger_usesLocalComponentsWithoutTimeZone() {
        val components = triggerComponents(LocalDateTime(2027, 3, 10, 9, 0))

        assertThat(components.year).isEqualTo(2027L)
        assertThat(components.month).isEqualTo(3L)
        assertThat(components.day).isEqualTo(10L)
        assertThat(components.hour).isEqualTo(9L)
        assertThat(components.minute).isEqualTo(0L)
        assertThat(components.timeZone).isNull()
    }

    /** A trigger at 09:00:00 for a 09:00:45 reminder scheduled at 09:00:20 would never fire. */
    @Test fun trigger_keepsTheSeconds() {
        assertThat(triggerComponents(LocalDateTime(2027, 3, 10, 9, 0, 45)).second).isEqualTo(45L)
    }

    @Test fun content_carriesTheDeepLink() {
        val content = content(CenterRequest("a", null, "Netflix eats in 3 days", "€13.99", "app://a"))

        assertThat(content.title).isEqualTo("Netflix eats in 3 days")
        assertThat(content.body).isEqualTo("€13.99")
        assertThat(content.userInfo["deepLink"] as String?).isEqualTo("app://a")
    }

    @Test fun reminderStores_areExcludedFromBackup() = runTest {
        val dir = (NSTemporaryDirectory() + "reminders-" + NSUUID().UUIDString).toPath()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val reminders = openIosReminders(dir, SilentCenter(), scope).reminders
            reminders.schedule(Reminder("a", LocalDateTime(2099, 1, 1, 9, 0), "c", "t", "x", null))

            assertThat(FileSystem.SYSTEM.exists(dir / "reminders.json")).isEqualTo(true)
            memScoped {
                val value = alloc<ObjCObjectVar<Any?>>()
                NSURL.fileURLWithPath(dir.toString()).getResourceValue(value.ptr, NSURLIsExcludedFromBackupKey, null)
                assertThat((value.value as NSNumber?)?.boolValue).isEqualTo(true)
            }
        } finally {
            scope.cancel()
            FileSystem.SYSTEM.deleteRecursively(dir)
        }
    }
}

private class SilentCenter : NotificationCenter {
    override fun add(request: CenterRequest) = Unit
    override fun removePending(ids: List<String>) = Unit
    override fun removeDelivered(ids: List<String>) = Unit
}

/**
 * iOS rejects every request until the app is authorized (UNErrorDomain 2003): what was planned before
 * (the first add, a restored phone, notifications switched back on in Settings) is armed again on
 * `rearmAll()`, which the app calls once notifications are allowed.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosRemindersRearmTest {
    @Test fun rearmAll_armsWhatIosRejectedBeforeAuthorization() = runTest {
        val dir = (NSTemporaryDirectory() + "reminders-" + NSUUID().UUIDString).toPath()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val center = AuthorizingCenter()
        try {
            val ios = openIosReminders(dir, center, scope)
            ios.reminders.schedule(Reminder("a", LocalDateTime(2099, 1, 1, 9, 0), "c", "t", "x", "app://a"))
            assertThat(center.pending.keys.toList()).isEqualTo(emptyList<String>())

            center.authorized = true
            ios.rearmAll()

            assertThat(center.pending.keys.toList()).isEqualTo(listOf("a"))
        } finally {
            scope.cancel()
            FileSystem.SYSTEM.deleteRecursively(dir)
        }
    }
}

/** Like UNUserNotificationCenter: an add before authorization is dropped. */
private class AuthorizingCenter : NotificationCenter {
    var authorized = false
    val pending = mutableMapOf<String, CenterRequest>()
    override fun add(request: CenterRequest) {
        if (authorized && request.at != null) pending[request.id] = request
    }
    override fun removePending(ids: List<String>) { ids.forEach { pending.remove(it) } }
    override fun removeDelivered(ids: List<String>) = Unit
}

