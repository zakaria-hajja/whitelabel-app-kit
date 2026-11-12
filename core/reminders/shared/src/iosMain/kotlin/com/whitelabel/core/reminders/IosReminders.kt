package com.whitelabel.core.reminders

import kotlin.time.Clock
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

/**
 * [Reminders] on iOS: add `iosRemindersModule()` to the app's Koin modules. The system shows each
 * reminder at its time; at most [MAX_PENDING_REQUESTS] are armed at once (the soonest), the rest wait
 * and are armed as slots free up and at each launch. The stores live in [directory], excluded from
 * backups like Android's `noBackupFilesDir`. Override `NotificationCenter` in tests.
 */
fun iosRemindersModule(directory: Path? = null): Module = module {
    single<NotificationCenter> { SystemNotificationCenter() }
    single {
        openIosReminders(directory ?: defaultRemindersDirectory(), get(), CoroutineScope(SupervisorJob() + Dispatchers.Default))
    }
    single { get<IosReminders>().reminders }
}

/**
 * iOS's [Reminders], plus [rearmAll]. iOS rejects every notification request until the app is
 * authorized, so what was planned before (the first add, before the permission sheet; a restored
 * phone; notifications switched back on in Settings) isn't armed. Call [rearmAll] whenever
 * notifications are allowed (on a grant and each return to the foreground): it hands every wanted
 * reminder to the system again, which replaces what's already there.
 */
class IosReminders internal constructor(val reminders: Reminders, private val port: CenterReminderPort) {
    suspend fun rearmAll() = port.rebalance(rearmAll = true)
}

/** Opens the stores in [directory] and, once, removes what the system already showed and re-arms the rest. */
internal fun openIosReminders(directory: Path, center: NotificationCenter, scope: CoroutineScope): IosReminders {
    FileSystem.SYSTEM.createDirectories(directory)
    excludeFromBackup(directory)
    val port = CenterReminderPort(
        inner = ReminderStore(directory / "reminders.json", scope),
        armedStore = ArmedStore(directory / "reminders-armed.json", scope),
        center = center,
        now = ::localNow,
    )
    val engine = ReminderEngine(port.store, port, port, ::localNow)
    scope.launch {
        port.sweepDelivered()
        engine.onSystemChange()
        port.rebalance(rearmAll = true)
    }
    return IosReminders(Reminders(engine), port)
}

private fun localNow(): LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

@OptIn(ExperimentalForeignApi::class)
private fun defaultRemindersDirectory(): Path {
    val support = NSFileManager.defaultManager.URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null)
    return checkNotNull(support?.path) { "No Application Support directory" }.toPath() / "reminders"
}

@OptIn(ExperimentalForeignApi::class)
private fun excludeFromBackup(directory: Path) {
    NSURL.fileURLWithPath(directory.toString()).setResourceValue(true, NSURLIsExcludedFromBackupKey, null)
}
