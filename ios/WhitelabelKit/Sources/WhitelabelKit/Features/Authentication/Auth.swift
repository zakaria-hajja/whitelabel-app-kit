import SwiftUI
import Shared

/** What the sign-in screens offer besides signing in. */
public struct AuthOptions {
    public var allowRegistration: Bool
    public var allowPasswordReset: Bool

    public init(allowRegistration: Bool = true, allowPasswordReset: Bool = true) {
        self.allowRegistration = allowRegistration
        self.allowPasswordReset = allowPasswordReset
    }
}

/**
 * Makes the whole app sign-in only (the gate), with email and password sign-in, registration and
 * password reset, as Android's `auth()`. For the reset link to open the app, the tenant's
 * `passwordReset.linkBaseUrl` must be `<app.deepLinkScheme>://reset-password`.
 */
public struct Auth: Feature {
    private let options: AuthOptions

    public init(options: AuthOptions = .init()) {
        self.options = options
    }

    public var spec: FeatureSpec {
        FeatureSpec(id: "auth", flagKey: nil, tabRoute: nil, requiresSignIn: false, isGate: true, requiresBackend: true)
    }

    public func tab() -> TabItem? { nil }

    @MainActor public func gateRoot() -> AnyView? { AnyView(LoginScreen(options: options)) }

    @MainActor public func routes(_ route: String, _ args: [String: String]) -> AnyView? {
        switch route {
        case AuthRoutes.shared.LOGIN: AnyView(LoginScreen(options: options))
        case AuthRoutes.shared.REGISTER: AnyView(RegisterScreen())
        case AuthRoutes.shared.FORGOT: AnyView(ForgotPasswordScreen())
        case AuthRoutes.shared.RESET: AnyView(ResetPasswordScreen(token: args["token"] ?? ""))
        default: nil
        }
    }

    /** `<scheme>://reset-password?token=…`, from the reset email. */
    public func destination(forLink url: URL) -> Destination? {
        guard url.host == "reset-password" else { return nil }
        let token = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.first { $0.name == "token" }?.value ?? ""
        return Destination(AuthRoutes.shared.RESET, args: ["token": token])
    }
}
