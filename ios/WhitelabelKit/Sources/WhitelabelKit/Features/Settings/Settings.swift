import SwiftUI
import Shared

/** Adds the settings tab: theme, language, notifications, about, legal links, sign out. Works without `Auth()`. */
public struct Settings: Feature {
    private let options: SettingsOptions

    public init(options: SettingsOptions = .init()) {
        self.options = options
    }

    public var spec: FeatureSpec {
        FeatureSpec(id: "settings", flagKey: nil, tabRoute: SettingsRoutes.shared.ROOT, requiresSignIn: false, isGate: false, requiresBackend: false)
    }

    public func tab() -> TabItem? {
        TabItem(route: SettingsRoutes.shared.ROOT, title: KitStrings.text("Settings"), systemImage: "gearshape")
    }

    @MainActor public func routes(_ route: String, _ args: [String: String]) -> AnyView? {
        guard route == SettingsRoutes.shared.ROOT else { return nil }
        return AnyView(SettingsScreen(options: options))
    }
}

/**
 * [showNotifications] shows the notification toggles (signed in only). [languages] are the BCP 47
 * tags the app is translated into (filtered by the backend's list); empty hides the language row.
 * [sections] are the app's own, above the shared ones. [aboutNote] follows the name and version on the About line.
 */
public struct SettingsOptions {
    public var showNotifications: Bool
    public var languages: [String]
    public var sections: [SettingsSection]
    public var aboutNote: String?

    public init(showNotifications: Bool = true, languages: [String] = ["en", "fr"], sections: [SettingsSection] = [], aboutNote: String? = nil) {
        self.showNotifications = showNotifications
        self.languages = languages
        self.sections = sections
        self.aboutNote = aboutNote
    }
}
