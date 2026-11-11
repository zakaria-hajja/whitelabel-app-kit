import SwiftUI
import Shared

/** Create an account; registering signs in too, and the shell then shows the app. */
struct RegisterScreen: View {
    @State private var screen = ScreenModel {
        let viewModel = SharedGraph.shared.registerViewModel()
        return SharedModel<RegisterState, RegisterAction>(holder: ViewModelHolder(viewModel: viewModel), state: viewModel.state, onAction: viewModel.onAction)
    }

    var body: some View {
        RegisterForm(state: screen.model.state, send: screen.model.send)
    }
}

struct RegisterForm: View {
    let state: RegisterState
    let send: (RegisterAction) -> Void

    var body: some View {
        AuthForm(title: KitStrings.text("Create an account")) {
            SharedTextField(label: KitStrings.text("Email"), text: Binding(get: { state.email }, set: { send(RegisterActionOnEmailChange(email: $0)) }), error: state.emailError)
                .emailInput()
            SharedTextField(label: KitStrings.text("Password"), text: Binding(get: { state.password }, set: { send(RegisterActionOnPasswordChange(password: $0)) }), error: state.passwordError, isSecure: true)
            SharedTextField(label: KitStrings.text("First name (optional)"), text: Binding(get: { state.firstName }, set: { send(RegisterActionOnFirstNameChange(name: $0)) }), error: state.firstNameError)
                .textContentType(.givenName)
            SharedTextField(label: KitStrings.text("Last name (optional)"), text: Binding(get: { state.lastName }, set: { send(RegisterActionOnLastNameChange(name: $0)) }), error: state.lastNameError)
                .textContentType(.familyName)
            FormError(error: state.error)
            SubmitButton(label: KitStrings.text("Create account"), enabled: state.canSubmit, loading: state.isSubmitting) {
                send(RegisterActionOnSubmit.shared)
            }
        }
    }
}

#Preview("Register") {
    PackPreviews {
        RegisterForm(
            state: RegisterState(email: "ada@example.com", password: "", firstName: "", lastName: "", emailError: nil, passwordError: nil, firstNameError: nil, lastNameError: nil, error: nil, isSubmitting: false),
            send: { _ in }
        )
        .frame(height: 640)
    }
}
