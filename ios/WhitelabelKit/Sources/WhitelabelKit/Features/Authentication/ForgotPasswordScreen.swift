import SwiftUI
import Shared

/** Ask for a reset link. The confirmation never says whether the account exists. */
struct ForgotPasswordScreen: View {
    @State private var screen = ScreenModel {
        let viewModel = SharedGraph.shared.forgotPasswordViewModel(email: nil)
        return SharedModel<ForgotPasswordState, ForgotPasswordAction>(holder: ViewModelHolder(viewModel: viewModel), state: viewModel.state, onAction: viewModel.onAction)
    }
    @SwiftUI.Environment(Navigator.self) private var navigator: Navigator?

    var body: some View {
        ForgotPasswordForm(state: screen.model.state, send: screen.model.send) { navigator?.pop() }
    }
}

struct ForgotPasswordForm: View {
    let state: ForgotPasswordState
    let send: (ForgotPasswordAction) -> Void
    let onBack: () -> Void
    @SwiftUI.Environment(\.appStyle) private var style

    var body: some View {
        AuthForm(title: KitStrings.text("Reset your password")) {
            if state.sent {
                Text(KitStrings.text("If an account exists for this email, a reset link is on its way. Check your inbox."))
                    .font(style.typography.body)
                Button(KitStrings.text("Back to sign in"), action: onBack).buttonStyle(TextButtonStyle())
            } else {
                Text(KitStrings.text("Enter your email and we'll send you a link to choose a new password."))
                    .font(style.typography.body)
                SharedTextField(label: KitStrings.text("Email"), text: Binding(get: { state.email }, set: { send(ForgotPasswordActionOnEmailChange(email: $0)) }), error: state.emailError)
                    .emailInput()
                FormError(error: state.error)
                SubmitButton(label: KitStrings.text("Send link"), enabled: state.canSubmit, loading: state.isSubmitting) {
                    send(ForgotPasswordActionOnSubmit.shared)
                }
            }
        }
    }
}

#Preview("Forgot password") {
    PackPreviews {
        ForgotPasswordForm(state: ForgotPasswordState(email: "ada@example.com", emailError: nil, error: nil, isSubmitting: false, sent: false), send: { _ in }, onBack: {})
            .frame(height: 420)
        ForgotPasswordForm(state: ForgotPasswordState(email: "ada@example.com", emailError: nil, error: nil, isSubmitting: false, sent: true), send: { _ in }, onBack: {})
            .frame(height: 300)
    }
}
