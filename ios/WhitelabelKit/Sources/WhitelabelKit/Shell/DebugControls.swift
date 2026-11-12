#if DEBUG
import SwiftUI
import Shared

/**
 * Debug launch arguments and links for UI tests and manual checks, as Android's debug controls:
 * - `-mockApi`: run on the in-memory fake backend;
 * - `-freshInstall`: start as a new install (no stores, no Keychain key, device language, no fake sessions);
 * - `-dropTokenKey`: delete the Keychain key only, as after a reinstall or a restored backup;
 * - `-fakeResetToken t1`: the fake issues reset token `t1`, already issued for alice@shared.test;
 * - `-fakeFeatures catalog=false,profile=true`: the fake's remote flags;
 * - `-fakeConfigDelayMs 5000`: the fake answers the config that late;
 * - `-messageSeconds 10`: one-off messages stay that long (UI tests read them after a gesture);
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

    private static var sessionsFile: URL? {
        FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first?.appendingPathComponent("whitelabel-fake-sessions.json")
    }

    /** Before the graph starts: what a fresh install (or a lost key) would not have. */
    func beforeStart(bundle: Bundle) {
        let messageSeconds = defaults.double(forKey: "messageSeconds")
        if messageSeconds > 0 { MessageBanner.seconds = messageSeconds }
        let cipher = KeychainTokenCipher(service: bundle.bundleIdentifier ?? "whitelabel")
        if arguments.contains("-dropTokenKey") { cipher.deleteKey() }
        guard arguments.contains("-freshInstall") else { return }
        if let file = Self.sessionsFile { try? FileManager.default.removeItem(at: file) }
        if let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first {
            try? FileManager.default.removeItem(at: support.appendingPathComponent("datastore"))
        }
        cipher.deleteKey()
        IosLanguageRepository().set(languageTag: nil)
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
