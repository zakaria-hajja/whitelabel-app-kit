import SwiftUI
import Shared

/** Opened from the emailed link (`<scheme>://reset-password?token=…`): choose a new password. */
struct ResetPasswordScreen: View {
    let token: String

    @State private var screen: ScreenModel<ResetPasswordState, ResetPasswordAction>
    @SwiftUI.Environment(Navigator.self) private var navigator: Navigator?

    init(token: String) {
        self.token = token
        _screen = State(initialValue: ScreenModel {
            let viewModel = SharedGraph.shared.resetPasswordViewModel(token: token)
            return SharedModel(holder: ViewModelHolder(viewModel: viewModel), state: viewModel.state, onAction: viewModel.onAction)
        })
    }

    var body: some View {
        ResetPasswordForm(
            state: screen.model.state,
            send: screen.model.send,
            onBackToSignIn: { navigator?.pop() },
            onRequestNewLink: { navigator?.push(Destination(AuthRoutes.shared.FORGOT)) }
        )
    }
}

struct ResetPasswordForm: View {
    let state: ResetPasswordState
    let send: (ResetPasswordAction) -> Void
    let onBackToSignIn: () -> Void
    let onRequestNewLink: () -> Void
    @SwiftUI.Environment(\.appStyle) private var style

    var body: some View {
        AuthForm(title: KitStrings.text("Choose a new password")) {
            if state.done {
                Text(KitStrings.text("Your password has been changed. Sign in with your new password.")).font(style.typography.body)
                SubmitButton(label: KitStrings.text("Back to sign in"), action: onBackToSignIn)
            } else if state.tokenExpired {
                Text(KitStrings.text("This link has expired or was already used.")).font(style.typography.body)
                SubmitButton(label: KitStrings.text("Request a new link"), action: onRequestNewLink)
            } else {
                SharedTextField(label: KitStrings.text("New password"), text: Binding(get: { state.password }, set: { send(ResetPasswordActionOnPasswordChange(password: $0)) }), error: state.passwordError, isSecure: true)
                    .textContentType(.newPassword)
                FormError(error: state.error)
                SubmitButton(label: KitStrings.text("Save password"), enabled: !state.password.isEmpty, loading: state.isSubmitting) {
                    send(ResetPasswordActionOnSubmit.shared)
                }
            }
        }
    }
}

#Preview("Reset password") {
    PackPreviews {
        ResetPasswordForm(state: ResetPasswordState(password: "", passwordError: nil, error: nil, isSubmitting: false, tokenExpired: false, done: false), send: { _ in }, onBackToSignIn: {}, onRequestNewLink: {})
            .frame(height: 300)
        ResetPasswordForm(state: ResetPasswordState(password: "", passwordError: nil, error: nil, isSubmitting: false, tokenExpired: true, done: false), send: { _ in }, onBackToSignIn: {}, onRequestNewLink: {})
            .frame(height: 260)
    }
}
