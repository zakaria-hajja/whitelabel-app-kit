import Foundation
import Shared

/**
 * The app's own language on iOS. The choice is stored and written to the app's `AppleLanguages`,
 * which iOS applies the next time the app starts; `nil` follows the device again.
 */
public final class IosLanguageRepository: NSObject, LanguageRepository {
    private let defaults: UserDefaults
    private static let key = "wl.appLanguage"

    public init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    public func current() -> String? {
        defaults.string(forKey: Self.key)
    }

    public func set(languageTag: String?) {
        if let languageTag {
            defaults.set(languageTag, forKey: Self.key)
            defaults.set([languageTag], forKey: "AppleLanguages")
        } else {
            defaults.removeObject(forKey: Self.key)
            defaults.removeObject(forKey: "AppleLanguages")
        }
    }
}
