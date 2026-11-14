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

Add `remindersModule(config)` to the app definition, with the app's notification channels. The
config is optional: without one, every reminder goes to a single channel named "Reminders".

```kotlin
appDefinition {
    // …
    modules(
        remindersModule(
            ReminderConfig(channels = listOf(ReminderChannel(id = "renewals", name = R.string.channel_renewals))),
        ),
    )
}
```

Get `Reminders` from Koin (`get()` in a module, a constructor parameter of a ViewModel). The module
creates the channels the first time `Reminders` is created. A reminder whose `channel`
isn't declared goes to the default "Reminders" channel; Android would otherwise drop it silently.

The small icon is `R.drawable.brand_notification_icon`, a white bell. Override it by adding a
drawable with the same name to the app (white on transparent, 24 dp).

The manifest entries (the permissions `POST_NOTIFICATIONS` and `RECEIVE_BOOT_COMPLETED`, and the
two receivers) merge into the app automatically.

## Scheduling

Get `Reminders` from Koin and give each reminder a stable id that the app chooses. Times are
`kotlinx.datetime.LocalDateTime`, local to the device:

```kotlin
reminders.schedule(
    Reminder(
        id = "netflix:renewal",
        at = LocalDateTime(2027, 3, 10, 9, 0),
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

  Pass `postPast = false` when the list may be stale by the time `setAll` runs, for example a replan
  at app start that can race a reminder's alarm or a boot broadcast. Then `setAll` never posts: a
  reminder whose time has passed is left exactly as stored (its alarm shows it once, even if the new
  list changed it) or, if it isn't stored, dropped as already shown or missed. A reminder can't show
  twice because an alarm fired between the app's read and its `setAll`. The default (`true`) keeps the
  late rule below.
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
  passed follows the same rule (posted now if recent), except `setAll(..., postPast = false)`, which
  never posts.
- **Blocked notifications.** Scheduling always works. If notifications are blocked when the time
  comes, nothing is shown and the reminder is removed; the app isn't told.
- **The content is the app's.** The module never groups, throttles or rewords notifications.
- **Force stops.** A force stop (from Settings, or from some phone makers' battery managers) removes
  every alarm and no broadcast follows. The module re-arms everything stored when `Reminders` is
  first created in a process, so inject it at app start (or soon after) if the app relies on reminders.
- **Not backed up.** Pending reminders stay on the phone (`noBackupFilesDir`): a restored copy would
  have no alarms armed. After a restore nothing is pending, so an app whose data is backed up should
  replan its reminders with `setAll` when it starts.

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

## iOS

The same `Reminders` API and engine, on `UNUserNotificationCenter`. Add `iosRemindersModule()` to
the app's Koin modules (on iOS, `AppDefinition(modules:)`). iOS shows each notification at its time
without running the app, which changes three things:

- **At most 64 armed.** iOS keeps 64 pending requests per app and silently drops the rest. The
  module arms the 64 soonest reminders. Later ones wait in the store and are armed as slots free
  up (any change) and at each launch. An app that plans more than 64 ahead should keep its total
  under the cap (Munch caps creatures for this) or accept that far-off reminders arm only when the
  app runs.
- **Shown reminders.** A reminder whose time has passed and that was armed was shown by iOS: it's
  no longer pending and is removed silently. One that was never armed (beyond the cap) follows the
  12-hour late rule at the next launch.
- **Time zones.** The trigger is the local date and time with no time zone, so 09:00 stays 09:00
  after a flight, as on Android.

- **Before permission.** iOS rejects every request until the app is authorized
  (`UNErrorDomain` 2003), so reminders planned before then (the first add, before the permission
  ask; a restored phone; notifications switched back on in Settings) aren't armed. Get
  `IosReminders` from Koin and call `rearmAll()` whenever notifications are allowed: on a grant and
  each return to the foreground. It hands every wanted reminder to the system again, which replaces
  what's already there.

The stores live in `Application Support/reminders/`, excluded from backups as on Android. After a
restore nothing is pending, so replan with `setAll` at launch.

**Permission:** WhitelabelKit's `NotificationPermission` (see [ios.md](ios.md#platform-pieces-swift)).
iOS asks once: `.notAskedYet` → `request()` shows the system alert; after a refusal the status is
`.blocked` and only `openSettings()` helps. There is no Android-style "denied, ask again". Debug
builds take `-notificationAccess notAskedYet|granted|blocked` for UI tests.

**Taps:** the deep link is in the request's `userInfo["deepLink"]`. WhitelabelKit's
`ReminderNotifications` opens it through the shell's link handling and shows reminders while the
app is in the foreground. `-freshInstall` (Debug) also clears the reminders and every scheduled
notification.

