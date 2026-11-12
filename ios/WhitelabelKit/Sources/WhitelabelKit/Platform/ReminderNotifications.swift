import UserNotifications

/**
 * Shows reminders while the app is open (as Android does) and opens a tapped reminder's deep link
 * (`userInfo["deepLink"]`, set by the shared iOS reminders) through the shell's link handling, which
 * waits until content shows. `RootView` installs and connects it.
 *
 * For a tap that launches the app, iOS needs the delegate before launching finishes: call
 * `ReminderNotifications.shared.install()` in the app's `init()`. A tap that arrives before the shell
 * is ready is kept and delivered when it connects.
 */
@MainActor
public final class ReminderNotifications: NSObject, UNUserNotificationCenterDelegate {
    public static let shared = ReminderNotifications()

    static let foregroundPresentation: UNNotificationPresentationOptions = [.banner, .sound, .list]

    private var open: ((URL) -> Void)?
    private var pending: URL?
    private var installed = false

    /** Makes this the notification center's delegate; safe to call more than once. */
    public func install() {
        guard !installed else { return }
        installed = true
        UNUserNotificationCenter.current().delegate = self
    }

    /** Where taps go from now on; a tap that came earlier is delivered now. */
    func connect(_ open: @escaping (URL) -> Void) {
        self.open = open
        if let pending {
            self.pending = nil
            open(pending)
        }
    }

    func handleTap(userInfo: [AnyHashable: Any]) {
        guard let link = userInfo["deepLink"] as? String, !link.isEmpty, let url = URL(string: link) else { return }
        if let open { open(url) } else { pending = url }
    }

    public nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler(Self.foregroundPresentation)
    }

    public nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let userInfo = response.notification.request.content.userInfo
        MainActor.assumeIsolated { handleTap(userInfo: userInfo) }
        completionHandler()
    }
}
