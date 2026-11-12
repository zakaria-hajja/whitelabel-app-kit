import Foundation
import FirebaseRemoteConfig
import Shared

/** Minimum fetch interval: none in Debug, so changes show at once; 12 hours in Release (as on Android). */
enum FetchInterval {
    static let debugSeconds: TimeInterval = 0
    static let releaseSeconds: TimeInterval = 43_200

    static func seconds(debugBuild: Bool) -> TimeInterval { debugBuild ? debugSeconds : releaseSeconds }
}

/** The shared `RemoteConfigSource` over Firebase Remote Config. A fresh fetch is allowed every `minimumFetchInterval`. */
final class FirebaseRemoteConfigSource: NSObject, Shared.RemoteConfigSource {
    private let config: RemoteConfig

    init(config: RemoteConfig, minimumFetchInterval: TimeInterval) {
        self.config = config
        let settings = RemoteConfigSettings()
        settings.minimumFetchInterval = minimumFetchInterval
        config.configSettings = settings
    }

    func raw(key: String) -> String? {
        let value = config.configValue(forKey: key)
        // Static means "no remote value and no in-app default": absent, not an empty string.
        return value.source == FirebaseRemoteConfig.RemoteConfigSource.static ? nil : value.stringValue
    }

    /** Fetches, then activates; calls back true only when the activation changed the values. Never throws. */
    func fetchAndActivate(onDone: @escaping (KotlinBoolean) -> Void) {
        config.fetch { [config] status, error in
            guard status == .success, error == nil else { return onDone(KotlinBoolean(bool: false)) }
            config.activate { changed, error in onDone(KotlinBoolean(bool: changed && error == nil)) }
        }
    }
}
