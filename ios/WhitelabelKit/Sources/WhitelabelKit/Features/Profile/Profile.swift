import SwiftUI
import Shared

/**
 * Adds the profile tab: the signed-in user's email and names, and a password change. Needs `Auth()`.
 * Its flag key is `profile`: list it in `defaults.features` (or switch it on remotely) for the tab to show.
 */
public struct Profile: Feature {
    private let allowPasswordChange: Bool

    public init(allowPasswordChange: Bool = true) {
        self.allowPasswordChange = allowPasswordChange
    }

    public var spec: FeatureSpec {
        FeatureSpec(id: "profile", flagKey: "profile", tabRoute: ProfileRoutes.shared.ROOT, requiresSignIn: true, isGate: false, requiresBackend: false)
    }

    public func tab() -> TabItem? {
        TabItem(route: ProfileRoutes.shared.ROOT, title: KitStrings.text("Profile"), systemImage: "person")
    }

    @MainActor public func routes(_ route: String, _ args: [String: String]) -> AnyView? {
        switch route {
        case ProfileRoutes.shared.ROOT: AnyView(ProfileScreen(allowPasswordChange: allowPasswordChange))
        case ProfileRoutes.shared.CHANGE_PASSWORD where allowPasswordChange: AnyView(ChangePasswordScreen())
        default: nil
        }
    }
}
