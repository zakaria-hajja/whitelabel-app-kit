package com.whitelabel.core.reminders

import kotlinx.datetime.LocalDateTime
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

/** [NotificationCenter] on `UNUserNotificationCenter`: iOS shows each request at its time, with no app code running. */
class SystemNotificationCenter : NotificationCenter {
    private val center: UNUserNotificationCenter get() = UNUserNotificationCenter.currentNotificationCenter()

    override fun add(request: CenterRequest) {
        val trigger = request.at?.let { UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(triggerComponents(it), repeats = false) }
        center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier(request.id, content(request), trigger), withCompletionHandler = null)
    }

    override fun removePending(ids: List<String>) = center.removePendingNotificationRequestsWithIdentifiers(ids)

    override fun removeDelivered(ids: List<String>) = center.removeDeliveredNotificationsWithIdentifiers(ids)
}

/**
 * The wall-clock date and time with no time zone, so iOS fires it at that local time wherever the
 * phone is (09:00 stays 09:00 after a flight), as Android does.
 */
internal fun triggerComponents(at: LocalDateTime): NSDateComponents = NSDateComponents().apply {
    year = at.year.toLong()
    month = at.month.ordinal.toLong() + 1
    day = at.day.toLong()
    hour = at.hour.toLong()
    minute = at.minute.toLong()
    second = at.second.toLong()
}

/** The text, sound and tap link; WhitelabelKit's notification delegate reads `userInfo["deepLink"]`. */
internal fun content(request: CenterRequest): UNMutableNotificationContent = UNMutableNotificationContent().apply {
    setTitle(request.title)
    setBody(request.text)
    setSound(UNNotificationSound.defaultSound())
    request.deepLink?.let { setUserInfo(mapOf<Any?, Any?>(DEEP_LINK_KEY to it)) }
}

/** The `userInfo` key holding a reminder's deep link. */
const val DEEP_LINK_KEY = "deepLink"
