import SwiftUI
import Shared

/** Sign in with email and password. Signing in flips the session; the shell then shows the app. */
struct LoginScreen: View {
    let options: AuthOptions

    @State private var screen = ScreenModel {
        let viewModel = SharedGraph.shared.loginViewModel(email: nil)
        return SharedModel<LoginState, LoginAction>(holder: ViewModelHolder(viewModel: viewModel), state: viewModel.state, onAction: viewModel.onAction)
    }
    @SwiftUI.Environment(Navigator.self) private var navigator: Navigator?
    @SwiftUI.Environment(\.appBrand) private var brand

    var body: some View {
        LoginForm(
            state: screen.model.state,
            appName: brand?.appName ?? "",
            options: options,
            send: screen.model.send,
            onCreateAccount: { navigator?.push(Destination(AuthRoutes.shared.REGISTER)) },
            onForgotPassword: { navigator?.push(Destination(AuthRoutes.shared.FORGOT)) }
        )
    }
}

struct LoginForm: View {
    let state: LoginState
    let appName: String
    let options: AuthOptions
    let send: (LoginAction) -> Void
    let onCreateAccount: () -> Void
    let onForgotPassword: () -> Void

    var body: some View {
        AuthForm(title: String(format: KitStrings.text("Sign in to %@"), appName)) {
            SharedTextField(label: KitStrings.text("Email"), text: Binding(get: { state.email }, set: { send(LoginActionOnEmailChange(email: $0)) }), error: state.emailError)
                .emailInput()
            SharedTextField(label: KitStrings.text("Password"), text: Binding(get: { state.password }, set: { send(LoginActionOnPasswordChange(password: $0)) }), isSecure: true)
            FormError(error: state.error)
            SubmitButton(label: KitStrings.text("Sign in"), enabled: state.canSubmit, loading: state.isSubmitting) {
                send(LoginActionOnSubmit.shared)
            }
            if options.allowPasswordReset {
                Button(KitStrings.text("Forgot password?"), action: onForgotPassword).buttonStyle(TextButtonStyle())
            }
            if options.allowRegistration {
                Button(KitStrings.text("Create an account"), action: onCreateAccount).buttonStyle(TextButtonStyle())
            }
            // Debug builds on the fake backend only: signs in as its seeded test account.
            if state.guestAvailable {
                Button(KitStrings.text("Continue as guest (debug)")) { send(LoginActionOnContinueAsGuest.shared) }
                    .buttonStyle(TextButtonStyle())
                    .disabled(state.isSubmitting)
                    .accessibilityIdentifier("auth.guest")
            }
        }
    }
}

#Preview("Sign in") {
    PackPreviews {
        LoginForm(
            state: LoginState(email: "ada@example.com", password: "", emailError: nil, error: nil, isSubmitting: false, guestAvailable: false),
            appName: "Sample", options: .init(), send: { _ in }, onCreateAccount: {}, onForgotPassword: {}
        )
        .frame(height: 520)
    }
}
