import SwiftUI
import UserNotifications

/** Whether reminders can show, as the apps act on it. iOS asks once: a refusal can only be undone in Settings. */
public enum NotificationStatus: Equatable, Sendable {
    case granted
    /** Never asked: `request()` shows the system alert. */
    case notAskedYet
    /** Only the Settings app can turn notifications on: `openSettings()`. */
    case blocked

    public static func from(_ status: UNAuthorizationStatus) -> NotificationStatus {
        switch status {
        case .notDetermined: .notAskedYet
        case .denied: .blocked
        case .authorized, .provisional, .ephemeral: .granted
        @unknown default: .blocked
        }
    }
}

/** What `NotificationPermission` needs from `UNUserNotificationCenter` (a fake in tests). */
public protocol NotificationAuthorizing: AnyObject {
    func authorizationStatus() async -> UNAuthorizationStatus
    func requestAuthorization(options: UNAuthorizationOptions) async throws -> Bool
}

extension UNUserNotificationCenter: NotificationAuthorizing {
    public func authorizationStatus() async -> UNAuthorizationStatus { await notificationSettings().authorizationStatus }
}

/**
 * The notification permission's current `status` (Android's `rememberNotificationPermission`). The
 * kit never asks by itself: the app shows its rationale first, then calls `request()`. Re-read when
 * the app comes back to the foreground with `.notificationPermissionRefresh(_:)`.
 *
 * Debug builds: `-notificationAccess notAskedYet|granted|blocked` fixes the status and makes
 * `request()` grant without the system alert, which UI tests can't answer reliably.
 */
@MainActor
@Observable
public final class NotificationPermission {
    public private(set) var status: NotificationStatus = .notAskedYet

    @ObservationIgnored private let center: NotificationAuthorizing
    @ObservationIgnored private var fixed: NotificationStatus?

    public convenience init() {
        self.init(center: UNUserNotificationCenter.current(), arguments: ProcessInfo.processInfo.arguments)
    }

    init(center: NotificationAuthorizing, arguments: [String]) {
        self.center = center
        #if DEBUG
        fixed = Self.debugStatus(arguments)
        if let fixed { status = fixed }
        #endif
    }

    public func refresh() async {
        if let fixed { status = fixed; return }
        status = NotificationStatus.from(await center.authorizationStatus())
    }

    /** Shows the system alert when not asked yet; otherwise just re-reads the status. */
    public func request() async {
        if fixed != nil {
            if fixed == .notAskedYet { fixed = .granted }
            status = fixed ?? status
            return
        }
        let current = NotificationStatus.from(await center.authorizationStatus())
        if current == .notAskedYet {
            _ = try? await center.requestAuthorization(options: [.alert, .sound, .badge])
        }
        await refresh()
    }

    /** The app's notification settings, the only place a blocked permission can be turned on. */
    public func openSettings() {
        guard let url = URL(string: UIApplication.openNotificationSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }

    private static func debugStatus(_ arguments: [String]) -> NotificationStatus? {
        guard let index = arguments.firstIndex(of: "-notificationAccess"), index + 1 < arguments.count else { return nil }
        switch arguments[index + 1] {
        case "granted": return .granted
        case "notAskedYet": return .notAskedYet
        case "blocked": return .blocked
        default: return nil
        }
    }
}

public extension View {
    /** Reads `permission` now and each time the app returns to the foreground (e.g. back from Settings). */
    func notificationPermissionRefresh(_ permission: NotificationPermission) -> some View {
        modifier(PermissionRefresh(permission: permission))
    }
}

private struct PermissionRefresh: ViewModifier {
    let permission: NotificationPermission
    @SwiftUI.Environment(\.scenePhase) private var phase

    func body(content: Content) -> some View {
        content
            .task { await permission.refresh() }
            .onChange(of: phase) { _, new in
                if new == .active { Task { await permission.refresh() } }
            }
    }
}
