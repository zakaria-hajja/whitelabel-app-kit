import SwiftUI
import Shared

/** Change the password; other devices are signed out, this one stays signed in. */
struct ChangePasswordScreen: View {
    @State private var screen = ScreenModel {
        let viewModel = SharedGraph.shared.changePasswordViewModel()
        return SharedModel<ChangePasswordState, ChangePasswordAction>(holder: ViewModelHolder(viewModel: viewModel), state: viewModel.state, onAction: viewModel.onAction)
    }
    @SwiftUI.Environment(Navigator.self) private var navigator: Navigator?

    var body: some View {
        ChangePasswordForm(state: screen.model.state, send: screen.model.send) { navigator?.pop() }
            .navigationTitle(KitStrings.text("Change password"))
            .navigationBarTitleDisplayMode(.inline)
    }
}

struct ChangePasswordForm: View {
    let state: ChangePasswordState
    let send: (ChangePasswordAction) -> Void
    let onBack: () -> Void
    @SwiftUI.Environment(\.appStyle) private var style

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.md) {
                if state.done {
                    Text(KitStrings.text("Your password has been changed. Other devices were signed out.")).font(style.typography.body)
                    SubmitButton(label: KitStrings.text("Back to profile"), action: onBack)
                } else {
                    SharedTextField(label: KitStrings.text("Current password"), text: Binding(get: { state.current }, set: { send(ChangePasswordActionOnCurrentChange(password: $0)) }), error: state.currentError, isSecure: true)
                        .textContentType(.password)
                    SharedTextField(label: KitStrings.text("New password"), text: Binding(get: { state.new }, set: { send(ChangePasswordActionOnNewChange(password: $0)) }), error: state.newError, isSecure: true)
                        .textContentType(.newPassword)
                    SharedTextField(label: KitStrings.text("Confirm new password"), text: Binding(get: { state.confirm }, set: { send(ChangePasswordActionOnConfirmChange(password: $0)) }), error: state.confirmError, isSecure: true)
                        .textContentType(.newPassword)
                    FormError(error: state.error)
                    SubmitButton(label: KitStrings.text("Change password"), enabled: state.canSubmit, loading: state.isSubmitting) {
                        send(ChangePasswordActionOnSubmit.shared)
                    }
                }
            }
            .padding(style.pack.sizing.screenGutter)
        }
        .scrollDismissesKeyboard(.interactively)
    }
}

#Preview("Change password") {
    PackPreviews {
        ChangePasswordForm(
            state: ChangePasswordState(current: "", new: "", confirm: "", currentError: nil, newError: nil, confirmError: nil, error: nil, isSubmitting: false, done: false),
            send: { _ in }, onBack: {}
        )
        .frame(height: 420)
    }
}
