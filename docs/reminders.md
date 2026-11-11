# Reminders (`:core:reminders`)

Local notifications at a date and time. They survive reboots, clock and time-zone changes, and app
updates. Tapping one opens a deep link. Nothing here talks to a server.

## Wiring

Add the module to the app:

```kotlin
dependencies {
    implementation(project(":core:reminders"))
}
```

Declare the app's notification channels with a Hilt `ReminderConfig`. It's optional: without one,
every reminder goes to a single channel named "Reminders".

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object MunchRemindersModule {
    @Provides
    fun reminderConfig() = ReminderConfig(
        channels = listOf(ReminderChannel(id = "renewals", name = R.string.channel_renewals)),
    )
}
```

The module creates the channels the first time `Reminders` is injected. A reminder whose `channel`
isn't declared goes to the default "Reminders" channel; Android would otherwise drop it silently.

The small icon is `R.drawable.brand_notification_icon`, a white bell. Override it by adding a
drawable with the same name to the app (white on transparent, 24 dp).

The manifest entries (the permissions `POST_NOTIFICATIONS` and `RECEIVE_BOOT_COMPLETED`, and the
two receivers) merge into the app automatically.

## Scheduling

Inject `Reminders` and give each reminder a stable id that the app chooses:

```kotlin
reminders.schedule(
    Reminder(
        id = "netflix:renewal",
        at = LocalDateTime.of(2027, 3, 10, 9, 0),
        channel = "renewals",
        title = "Netflix renews tomorrow",
        text = "€13.99 on your card",
        deepLink = "munch://creature/netflix",
    ),
)
```

- `schedule` replaces a reminder with the same id and disarms its old alarm, so only one ever fires.
- `setAll(reminders, idPrefix)` makes the pending reminders under a prefix exactly the given list.
  New and changed ones are armed, unchanged ones are left alone, and the rest under the prefix are
  cancelled. Use it when the app replans a whole set after a change, so it doesn't have to remember
  what it scheduled before. Every id in the list must start with the prefix.

  ```kotlin
  // After editing Netflix: its reminders become exactly these two; other creatures' stay as they are.
  reminders.setAll(listOf(renewalSoon, hungryToday), idPrefix = "netflix:")
  ```
- `cancel(id)`, `cancelAll()` and `pending()` do what they say.

`at` is a local date and time. It's read in the phone's current time zone, so a 09:00 reminder
still fires at 09:00 after the user flies somewhere else.

## The rules

- **Timing is near the minute, not exact.** Alarms use `setAndAllowWhileIdle`, which fires in Doze
  but may be a few minutes late. The module doesn't use exact alarms: they need a permission that
  Play keeps for alarm-clock and calendar apps.
- **Reboots, clock changes, time-zone changes and app updates** clear or shift alarms. The module
  re-arms every pending reminder after each of them.
- **The 12-hour rule.** A reminder whose time passed while the phone was off is shown once on boot
  if it's less than 12 hours late; an older one is dropped. Scheduling a time that has already
  passed follows the same rule.
- **Blocked notifications.** Scheduling always works. If notifications are blocked when the time
  comes, nothing is shown and the reminder is removed; the app isn't told.
- **The content is the app's.** The module never groups, throttles or rewords notifications.
- **Not backed up.** Pending reminders stay on the phone (`noBackupFilesDir`): a restored copy would
  have no alarms armed. After a restore the app replans them, as it does after any change.

## Permission

On Android 13+ notifications need the user's permission. The module never asks by itself: the app
shows its own rationale first, at a moment that makes sense (Munch does it after the first
subscription is added).

```kotlin
val permission = rememberNotificationPermission()
when (permission.state) {
    NotificationPermission.Granted -> Unit
    NotificationPermission.NotAskedYet, NotificationPermission.Denied ->
        Button(onClick = permission::request) { Text("Turn on reminders") }   // the system dialog
    NotificationPermission.BlockedInSettings ->
        Button(onClick = { context.openNotificationSettings() }) { Text("Open settings") }
}
```

- `NotAskedYet`: never asked; `request()` shows the system dialog.
- `Denied`: refused once; the dialog can be shown one more time.
- `BlockedInSettings`: only system settings can turn notifications on (also every "off" state
  below Android 13). `request()` does nothing then.

The state is re-read whenever the screen resumes, so it updates when the user comes back from
settings.

## Tapping a notification

With a `deepLink`, a tap opens it as an `ACTION_VIEW` intent limited to the app's own package, so
the app's deep-link handling routes it (see the shell's deep-link scheme). Without one, a tap
opens the app. The notification disappears when tapped.
