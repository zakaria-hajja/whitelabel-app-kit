#if DEBUG
import SwiftUI
import Shared
import UserNotifications

/**
 * Debug launch arguments and links for UI tests and manual checks, as Android's debug controls:
 * - `-mockApi`: run on the in-memory fake backend;
 * - `-freshInstall`: start as a new install (no stores, no Keychain key, device language, no fake sessions);
 * - `-dropTokenKey`: delete the Keychain key only, as after a reinstall or a restored backup;
 * - `-fakeResetToken t1`: the fake issues reset token `t1`, already issued for alice@shared.test;
 * - `-fakeFeatures catalog=false,profile=true`: the fake's remote flags;
 * - `-fakeConfigDelayMs 5000`: the fake answers the config that late;
 * - `-messageSeconds 10`: one-off messages stay that long (UI tests read them after a gesture);
 * - `-notificationAccess notAskedYet|granted|blocked`: the notification permission reports that
 *   status, and asking grants without the system alert (see `NotificationPermission`);
 * - `-fakeFirebase`: Firebase runs as the in-memory fake (needs a Firebase service in the definition);
 * - `-analyticsDebug`: analytics and crash collection run in this debug build, and events go to
 *   Firebase's DebugView (`LaunchFlags.analyticsDebug`);
 * - `-consentEEA`: the consent form behaves as in the EEA (`LaunchFlags.consentEEA`);
 * - `-consentTestDevice <ids>`: UMP test devices, as UMP logs them; needed for `-consentEEA` on a
 *   real device or simulator (`LaunchFlags.consentTestDevices`);
 * - `-fakeConsent obtained|declined|failed|notRequired`: what the fake consent answers (default obtained);
 * - `<scheme>://_fake/offline|online|expire-tokens|revoke-sessions`: steer the running fake;
 * - `<scheme>://_fake/log`: show the fake's recent requests in the `debug.fakeLog` probe.
 *
 * The fake keeps its sessions in Caches across launches, as a real server would, so a relaunch
 * stays signed in (accounts registered in a run are not kept).
 */
struct DebugControls {
    let arguments: [String]
    private let defaults = UserDefaults.standard

    var useMockApi: Bool { arguments.contains("-mockApi") }
    var fakeFirebase: Bool { arguments.contains("-fakeFirebase") }
    var analyticsDebug: Bool { arguments.contains("-analyticsDebug") }
    var fakeConsent: String { defaults.string(forKey: "fakeConsent") ?? "obtained" }

    private static var sessionsFile: URL? {
        FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first?.appendingPathComponent("whitelabel-fake-sessions.json")
    }

    /** Before the graph starts: what a fresh install (or a lost key) would not have. */
    func beforeStart(bundle: Bundle) {
        let messageSeconds = defaults.double(forKey: "messageSeconds")
        if messageSeconds > 0 { MessageBanner.seconds = messageSeconds }
        applyLaunchFlags()
        SharedGraph.shared.useFirebaseDebug(fake: fakeFirebase, analytics: analyticsDebug, fakeConsent: fakeConsent)
        let cipher = KeychainTokenCipher(service: bundle.bundleIdentifier ?? "whitelabel")
        if arguments.contains("-dropTokenKey") { cipher.deleteKey() }
        guard arguments.contains("-freshInstall") else { return }
        if let file = Self.sessionsFile { try? FileManager.default.removeItem(at: file) }
        if let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first {
            try? FileManager.default.removeItem(at: support.appendingPathComponent("datastore"))
            try? FileManager.default.removeItem(at: support.appendingPathComponent("reminders"))
        }
        // A new install has no scheduled or shown notifications either.
        UNUserNotificationCenter.current().removeAllPendingNotificationRequests()
        UNUserNotificationCenter.current().removeAllDeliveredNotifications()
        cipher.deleteKey()
        IosLanguageRepository().set(languageTag: nil)
    }

    /** The arguments a service reads (`LaunchFlags`). */
    func applyLaunchFlags() {
        LaunchFlags.consentEEA = arguments.contains("-consentEEA")
        let deviceArgument = arguments.firstIndex(of: "-consentTestDevice").flatMap { index in
            arguments.indices.contains(index + 1) && !arguments[index + 1].hasPrefix("-") ? arguments[index + 1] : nil
        }
        LaunchFlags.consentTestDevices = DebugConsent.shared.testDeviceIds(raw: deviceArgument)
        LaunchFlags.analyticsDebug = analyticsDebug
    }

    /** After the graph starts, before the shell asks for the config: set up the fake. */
    func afterStart(_ fake: MockapiFakeBackend) {
        if let flags = defaults.string(forKey: "fakeFeatures") {
            for pair in flags.split(separator: ",") {
                let parts = pair.split(separator: "=", maxSplits: 1).map(String.init)
                guard parts.count == 2 else { continue }
                fake.features.setObject(KotlinBoolean(bool: parts[1] == "true"), forKey: parts[0] as NSString)
            }
        }
        let delay = defaults.integer(forKey: "fakeConfigDelayMs")
        if delay > 0 { fake.configDelayMs = Int64(delay) }
        if let token = defaults.string(forKey: "fakeResetToken") {
            fake.fixedResetToken = token
            _ = fake.issueResetToken(email: "alice@shared.test")
        }
        if let file = Self.sessionsFile {
            if let saved = try? String(contentsOf: file, encoding: .utf8) { fake.importSessions(json: saved) }
            fake.onSessionsChanged = { json in try? json.write(to: file, atomically: true, encoding: .utf8) }
        }
    }

    /** The fake's last requests, oldest first: `401 PATCH /api/v1/settings | 200 POST /api/v1/auth/refresh | …`. */
    @MainActor static func recentRequests(limit: Int = 12) -> String {
        IosMock.shared.fakeBackend().exchanges.suffix(limit)
            .map { "\($0.status) \($0.method.uppercased()) \($0.path)" }
            .joined(separator: " | ")
    }

    /** A `_fake/<command>` link. */
    @MainActor static func run(_ command: String) {
        let fake = IosMock.shared.fakeBackend()
        switch command {
        case "offline": fake.offline = true
        case "online": fake.offline = false
        case "expire-tokens": fake.expireAccessTokens()
        case "revoke-sessions": fake.revokeSessions()
        default: print("Unknown debug control: \(command)")
        }
    }
}

/** Invisible values UI tests read: the applied color scheme, the last link received, the fake's log. */
struct DebugProbes: View {
    let lastLink: String
    let fakeLog: String
    @SwiftUI.Environment(\.colorScheme) private var colorScheme

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            probe(colorScheme == .dark ? "dark" : "light", id: "debug.colorScheme")
            probe(lastLink, id: "debug.lastLink")
            probe(fakeLog, id: "debug.fakeLog")
        }
        .allowsHitTesting(false)
    }

    private func probe(_ value: String, id: String) -> some View {
        Text(value.isEmpty ? "-" : value)
            .font(.system(size: 1))
            .lineLimit(1)
            .frame(width: 2, height: 1, alignment: .leading)
            .opacity(0.01)
            .accessibilityIdentifier(id)
    }
}
#endif
