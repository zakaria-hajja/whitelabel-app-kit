#if DEBUG
import SwiftUI
import Shared

/**
 * Debug launch arguments and links for UI tests and manual checks, as Android's debug controls:
 * - `-mockApi`: run on the in-memory fake backend;
 * - `-freshInstall`: start as a new install (no stores, no Keychain key, device language);
 * - `-fakeFeatures catalog=false,profile=true`: the fake's remote flags;
 * - `-fakeConfigDelayMs 5000`: the fake answers the config that late;
 * - `<scheme>://_fake/offline|online|expire-tokens|revoke-sessions`: steer the running fake.
 */
struct DebugControls {
    let arguments: [String]
    private let defaults = UserDefaults.standard

    var useMockApi: Bool { arguments.contains("-mockApi") }

    /** Before the graph starts: what a fresh install would not have. */
    func beforeStart(bundle: Bundle) {
        guard arguments.contains("-freshInstall") else { return }
        if let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first {
            try? FileManager.default.removeItem(at: support.appendingPathComponent("datastore"))
        }
        KeychainTokenCipher(service: bundle.bundleIdentifier ?? "whitelabel").deleteKey()
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

/** The color scheme in effect, for UI tests (a theme change must apply at once). */
struct ColorSchemeProbe: View {
    @SwiftUI.Environment(\.colorScheme) private var colorScheme

    var body: some View {
        Text(colorScheme == .dark ? "dark" : "light")
            .font(.system(size: 1))
            .opacity(0.01)
            .allowsHitTesting(false)
            .accessibilityIdentifier("debug.colorScheme")
    }
}
#endif
