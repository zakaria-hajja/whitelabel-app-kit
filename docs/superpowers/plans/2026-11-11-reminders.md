# Reminders Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A shared `:core:reminders` module. Any app schedules local notifications for a date and time; they survive reboots, time-zone changes and app updates, and a tap opens a deep link.

**Architecture:**
- **The engine:** all decisions sit in `ReminderEngine`, a plain class with no Android code:
  - store, replace and cancel;
  - a desired-set `setAll`;
  - what to do after a reboot;
  - what to do when an alarm fires.

  It works through three small interfaces: a document store, an `AlarmScheduler` and a `NotificationPoster`. That keeps it unit-testable.
- **The Android side:**
  - an `AlarmManager.setAndAllowWhileIdle` scheduler;
  - two broadcast receivers: one for when an alarm fires, one for boot, time change and app update;
  - a `NotificationCompat` poster;
  - permission helpers.
- **Wiring:** a small process-wide `RemindersRuntime` builds the engine, so the receivers work without Hilt. Hilt then exposes the same instance to apps.

**Tech Stack:** Kotlin, `AlarmManager`, `NotificationManagerCompat`, `JsonStore`, kotlinx.serialization, Hilt, JUnit 5 + AssertK + coroutines-test, Android instrumented tests.

**Spec:** `docs/superpowers/specs/2026-11-10-standalone-app-support-design.md` §4 and §7 row 3. The consumer is Munch (`munch` spec §5): it plans reminders per creature with `plannedReminders(today)`, whose result is a list of `(creatureId, LocalDate, kind)`. A later Munch branch maps those to `Reminder`s at the user's reminder time.

**Branch:** `feat/reminders` from `origin/main` (7ed08d2), worktree `../android-starter-kit-reminders`.

## Global Constraints

- **`setAndAllowWhileIdle` only.** No exact-alarm permission; Play restricts it to alarm and calendar apps.
- **A reminder whose time passed while the phone was off fires once on boot if it's less than 12 hours late; otherwise it's dropped.**
- **The module never prompts on its own.** The rationale screen belongs to the app. The module offers `requestPermission` (the system dialog) and `openNotificationSettings`.
- **The content is the app's.** The module never groups, throttles or rewords.
- **No app identity in the module.** Channels and the small icon come from the app: the icon through a brand resource with a default.
- Shared modules never depend on apps. Conventional Commits with the Claude trailer, explicit-path staging, and `scripts/check.sh` before pushing.

## Deviation from the spec (decided while planning)

The spec has apps declare channels with a `reminders { channel(...) }` call in the app definition. The plan instead has apps provide a `ReminderConfig` (their channels) through Hilt, and the module creates the channels on first use and before posting.

The reason: a definition call would make `:core:shell` depend on `:core:reminders`, or need a generic extension slot in `AppDefinition`. Reminders work without the shell's UI at all (in receivers, after a reboot), so they shouldn't hang off the definition.

Second deviation: the spec puts `permission: StateFlow<NotificationPermission>` on `Reminders`. The plan exposes it as a composable state holder, `rememberNotificationPermission()`, because requesting the permission needs an Activity result launcher, and the state must be re-read when the user comes back from system settings (`ON_RESUME`).

## Review Focus

- **A time-zone change or a manual clock change:** pending reminders are re-registered for their local date and time in the new zone. (Task 2: the boot/time receiver routes to `engine.onSystemChange()`; Task 1 tests the decision.)
- **An app update** (`MY_PACKAGE_REPLACED`) clears alarms, so every pending reminder is re-registered. (Task 2)
- **Notifications blocked or never granted:** `schedule` still stores and arms the alarm. When it fires, posting is skipped without a crash, and the reminder is removed. (Task 1, `NotificationPoster` returning false; Task 2 poster.)
- **Replacing a reminder with the same id but a different time:** the old alarm is cancelled, so only one notification ever fires. (Task 1)
- **The receiver firing for a reminder that was cancelled meanwhile** (a race with the alarm): nothing is posted. (Task 1, `onAlarm` for an unknown id.)

---

### Task 1: The engine and its decisions (pure Kotlin)

**Files:**
- Create: `core/reminders/build.gradle.kts`:
  - plugins: `whitelabel.android.library`, `whitelabel.hilt`, `kotlin.serialization`;
  - deps: `:core:datastore`, `libs.kotlinx.serialization.json`, `libs.androidx.core.ktx` (if present in the catalog, otherwise `androidx.core` through the existing catalog entry used by `:core:shell`), and test `libs.turbine`.
- Modify: `gradle/foundation.settings.gradle.kts` (add `":core:reminders"`).
- Create in `core/reminders/src/main/kotlin/com/whitelabel/core/reminders/`:
  - `Reminder.kt`: the public model;
  - `ReminderEngine.kt`;
  - `Ports.kt`: `AlarmScheduler`, `NotificationPoster`, `ReminderStore`;
  - `StoredReminder.kt`: the serializable DTO.
- Test: `core/reminders/src/test/kotlin/com/whitelabel/core/reminders/ReminderEngineTest.kt`.

**Interfaces (produced):**
```kotlin
data class Reminder(
    val id: String,              // app-chosen, stable (e.g. "netflix:renewal")
    val at: LocalDateTime,       // local date-time, interpreted in the device's zone when armed
    val channel: String,         // a channel id from ReminderConfig
    val title: String,
    val text: String,
    val deepLink: String?,       // opened through the app's deep-link scheme; null opens the app
)
interface AlarmScheduler { fun set(id: String, at: LocalDateTime); fun cancel(id: String) }
interface NotificationPoster { /** false when notifications are blocked; nothing is shown */ fun post(reminder: Reminder): Boolean }
interface ReminderStore { val reminders: Flow<List<Reminder>>; suspend fun update(transform: (List<Reminder>) -> List<Reminder>) }

class ReminderEngine(private val store: ReminderStore, private val alarms: AlarmScheduler, private val poster: NotificationPoster, private val now: () -> LocalDateTime) {
    suspend fun schedule(reminder: Reminder)              // replaces same id: cancels its old alarm, stores, arms
    suspend fun setAll(reminders: List<Reminder>, idPrefix: String)  // the desired set under idPrefix: arms new/changed, cancels the rest under idPrefix
    suspend fun cancel(id: String)
    suspend fun cancelAll()
    suspend fun onAlarm(id: String)                       // posts if still stored and due; removes it
    suspend fun onSystemChange()                          // after boot/time/zone/update: re-arm future, fire < 12 h late, drop older
    suspend fun pending(): List<Reminder>
}
internal const val LATE_LIMIT_HOURS = 12L
```
- `setAll` is an addition to the spec's API. Munch replans a creature's whole set after every change, so it needs "make the stored set under this prefix exactly this". Without it, Munch would have to remember what it scheduled.
- A reminder whose `at` is already past when `schedule`/`setAll` runs is stored and posted at once if it's less than 12 hours late (the same rule as boot), otherwise ignored.

**Steps:**
- [ ] **Step 1: Write the failing tests.** `ReminderEngineTest` uses an in-memory store, a `FakeAlarms` (records `set`/`cancel`), a `FakePoster` (records posts; `blocked` flag returns false) and `now` = 2027-03-10T08:00.
  - `schedule stores and arms`: at 2027-03-10T09:00 → `pending()` has it, `alarms.set("a", 09:00)`.
  - `schedule with the same id replaces and cancels the old alarm`: 09:00, then 10:00 → one stored at 10:00, `cancel("a")` recorded before `set("a", 10:00)`.
  - `setAll arms the new set and cancels the rest of the prefix only`:
    - stored `m:a` 09:00, `m:b` 09:00, `x:z` 09:00;
    - `setAll([m:a 09:00, m:c 11:00], "m:")` → stored `m:a`, `m:c` and `x:z`;
    - `cancel("m:b")` recorded, `set("m:c", 11:00)` recorded;
    - **`m:a` is unchanged, so no re-arm** (no new `set("m:a")`).
  - `cancel and cancelAll`.
  - `onAlarm posts a stored due reminder and removes it`.
  - `onAlarm for an unknown id posts nothing`.
  - `onAlarm when notifications are blocked removes it without posting` (poster returns false; it's still removed).
  - `onAlarm before its time re-arms instead of posting` (an early wake, e.g. after a clock change): `at` = 09:00 and now = 08:00 → no post, `set("a", 09:00)`.
  - `onSystemChange re-arms future reminders, fires recent ones, drops old ones`:
    - stored: future 09:00 → `set`;
    - 11 h 59 m late (2027-03-09T20:01) → posted and removed;
    - 12 h 01 m late (2027-03-09T19:59) → dropped without posting.
  - `scheduling a past time follows the same late rule`.
- [ ] **Step 2: Run** `./gradlew :core:reminders:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3: Implement.** Compare `Reminder` by value to decide "unchanged" in `setAll`. The store keeps its list sorted by `at` then `id`.
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit** `feat(reminders): add the reminder engine`

### Task 2: Android side (alarms, receivers, notifications, permission, wiring)

**Files:**
- Create in `core/reminders/src/main/kotlin/com/whitelabel/core/reminders/`:
  - `AndroidAlarmScheduler.kt`: `setAndAllowWhileIdle(RTC_WAKEUP, …)`. The PendingIntent goes to `ReminderAlarmReceiver` with extra `id`; its request code is `id.hashCode()` and the intent's data is `reminder://<id>`, so PendingIntents for different ids stay distinct even when hashes collide.
  - `AndroidNotificationPoster.kt`: `NotificationCompat`, `setAutoCancel(true)`, `smallIcon = R.drawable.brand_notification_icon`. The content PendingIntent is `ACTION_VIEW` with the deep link (`setPackage(packageName)`) or, without one, the launch intent. It returns false when `!NotificationManagerCompat.areNotificationsEnabled()` or when Android 13+ hasn't granted `POST_NOTIFICATIONS`.
  - `ReminderConfig.kt`: `data class ReminderConfig(val channels: List<ReminderChannel>)` and `data class ReminderChannel(val id: String, @StringRes val name: Int, val importance: Int = NotificationManagerCompat.IMPORTANCE_DEFAULT)`. It also has `ensureChannels(context, config)`.
  - `RemindersRuntime.kt`: a process singleton (`RemindersRuntime.get(context)`) building the engine on `JsonStore(context.jsonStoreFile("reminders"))` with the Android ports and `LocalDateTime::now`. The config comes from `ReminderConfigProvider`: the Hilt-provided config is pushed in by the Hilt module, and receivers read it back. With no config, channels aren't created and posts use a "default" channel (see Step 3).
  - `Receivers.kt`:
    - `ReminderAlarmReceiver` → `engine.onAlarm(id)`;
    - `ReminderSystemReceiver` (`BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`, `MY_PACKAGE_REPLACED`) → `engine.onSystemChange()`;
    - both use `goAsync()` with a coroutine on `Dispatchers.Default`, falling back to `runBlocking` when `goAsync()` returns null (a direct call in tests).
  - `Permission.kt`:
    - `enum class NotificationPermission { Granted, NotAskedYet, Denied, BlockedInSettings }`;
    - `internal fun permissionState(enabled: Boolean, sdk: Int, askedBefore: Boolean, showRationale: Boolean): NotificationPermission`;
    - `@Composable fun rememberNotificationPermission(): NotificationPermissionState` with `val state` and `fun request()` (the system dialog through `rememberLauncherForActivityResult(RequestPermission)`; it records "asked" in `SharedPreferences("reminders")` and re-reads on `ON_RESUME`);
    - `fun Context.openNotificationSettings()` (`Settings.ACTION_APP_NOTIFICATION_SETTINGS`).
  - `Reminders.kt`: the public facade `class Reminders internal constructor(engine)`, exposing `schedule`, `setAll`, `cancel`, `cancelAll` and `pending`.
  - `RemindersModule.kt`: Hilt, `@Provides @Singleton fun reminders(@ApplicationContext context, config: Optional<ReminderConfig>): Reminders`. The config is bound by the app with `@BindsOptionalOf`, then pushed into `ReminderConfigProvider`.
- Create: `core/reminders/src/main/AndroidManifest.xml`:
  - `POST_NOTIFICATIONS` and `RECEIVE_BOOT_COMPLETED`;
  - the two receivers: `ReminderAlarmReceiver` not exported; `ReminderSystemReceiver` exported with the four actions.
- Create: `core/reminders/src/main/res/drawable/brand_notification_icon.xml` (a generic white bell vector, overridable by apps); `core/reminders/src/main/res/values/strings.xml` (`reminders_default_channel` = "Reminders").
- Test: `PermissionStateTest.kt` (unit); `core/reminders/src/androidTest/.../RemindersDeviceTest.kt` (instrumented).

**Interfaces (produced):** `Reminders` (as the engine's public subset), `ReminderConfig`, `ReminderChannel`, `NotificationPermission`, `rememberNotificationPermission()`, `Context.openNotificationSettings()`.

**Steps:**
- [ ] **Step 1: Write the failing tests.**
  - `PermissionStateTest`:
    - `enabled` → Granted;
    - SDK 33, not enabled, not asked → NotAskedYet;
    - asked and rationale shown → Denied (can ask again);
    - asked and no rationale → BlockedInSettings;
    - SDK 32 and not enabled → BlockedInSettings (there's nothing to request below 33).
  - `RemindersDeviceTest`, which grants `POST_NOTIFICATIONS` via `UiAutomation.grantRuntimePermission` and clears the `reminders` store first:
    - `a reminder fires and posts`: schedule `id = "t1"` at now + 3 s, title "Spotify is hungry today", channel "renewals" (the test config). Within 15 s, `NotificationManager.activeNotifications` contains a notification whose `EXTRA_TITLE` is that title, and `pending()` no longer has `t1`.
    - `system change fires a recent missed reminder and drops an old one`: store `late` at now − 1 h and `old` at now − 13 h through the engine's store. Call `ReminderSystemReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))` → `late` is posted, `old` isn't, and both are removed.
    - `the tap opens the deep link`: the posted notification's `contentIntent` is a PendingIntent that, sent (`send()`), starts an activity. Assert via an `Instrumentation.ActivityMonitor` for `Intent.ACTION_VIEW` with data `test://reminder/t1`.
- [ ] **Step 2: Run** `./gradlew :core:reminders:testDebugUnitTest` (expected: FAIL), then `:core:reminders:connectedDebugAndroidTest` on `emulator-5554` (expected: FAIL).
- [ ] **Step 3: Implement.** With no app config, the poster creates a single channel `"reminders"` named by `R.string.reminders_default_channel` and uses it for reminders whose channel is unknown. A post to an undeclared channel would otherwise be silently dropped on Android 8+.
- [ ] **Step 4: Run** both. Expected: PASS. Then `scripts/check.sh` and `./gradlew assembleDebug`. Expected: pass; no app depends on the module yet.
- [ ] **Step 5: Commit** `feat(reminders): schedule notifications that survive reboots`

### Task 3: Docs

**Files:**
- Create: `docs/reminders.md`:
  - **wiring:** add the dependency and provide a `ReminderConfig` with `@Provides`, override `drawable/brand_notification_icon`;
  - **scheduling:** `schedule` vs `setAll` (with an id-prefix example);
  - **the rules:** `setAndAllowWhileIdle` timing (near the minute, not exact), boot and update re-arming, the 12-hour late rule;
  - **permission:** `rememberNotificationPermission()` (the app shows its rationale first) and `openNotificationSettings()`;
  - **tap behavior:** deep links.
- Modify: `docs/architecture.md` (module table row) and `docs/creating-a-standalone-app.md` (one line pointing to `reminders.md`).

- [ ] **Step 1: Write the docs.** Every identifier matches Tasks 1–2.
- [ ] **Step 2: Verify** each identifier exists (`grep` in `core/reminders/src/main`).
- [ ] **Step 3: Commit** `docs: explain the reminders module`
