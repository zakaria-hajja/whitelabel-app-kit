import SwiftUI
import Shared

/** A button on the home screen that opens another tab, by its root route (e.g. `SettingsRoutes.shared.ROOT`). */
public struct HomeShortcut {
    public let title: String
    public let systemImage: String
    public let route: String

    public init(title: String, systemImage: String, route: String) {
        self.title = title
        self.systemImage = systemImage
        self.route = route
    }
}

/** Adds the home tab: a greeting for the signed-in user and the app's shortcuts (in order; one whose tab is missing or off is hidden). Needs `Auth()`. */
public struct Home: Feature {
    private let shortcuts: [HomeShortcut]

    public init(shortcuts: [HomeShortcut] = []) {
        self.shortcuts = shortcuts
    }

    public var spec: FeatureSpec {
        FeatureSpec(id: "home", flagKey: nil, tabRoute: HomeRoutes.shared.ROOT, requiresSignIn: true, isGate: false, requiresBackend: false)
    }

    public func tab() -> TabItem? {
        TabItem(route: HomeRoutes.shared.ROOT, title: KitStrings.text("Home"), systemImage: "house")
    }

    @MainActor public func routes(_ route: String, _ args: [String: String]) -> AnyView? {
        route == HomeRoutes.shared.ROOT ? AnyView(HomeScreen(shortcuts: shortcuts)) : nil
    }
}
