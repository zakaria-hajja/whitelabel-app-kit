import SwiftUI
import Shared

/** The signed-in user's email (shown) and names (edited), on the shared ProfileViewModel. */
struct ProfileScreen: View {
    let allowPasswordChange: Bool

    @State private var screen = ScreenModel {
        let viewModel = SharedGraph.shared.profileViewModel()
        return SharedModel<ProfileState, ProfileAction>(holder: ViewModelHolder(viewModel: viewModel), state: viewModel.state, onAction: viewModel.onAction)
    }
    @SwiftUI.Environment(Navigator.self) private var navigator: Navigator?

    var body: some View {
        let model = screen.model
        ProfileForm(
            state: model.state,
            allowPasswordChange: allowPasswordChange,
            send: model.send,
            onChangePassword: { navigator?.push(Destination(ProfileRoutes.shared.CHANGE_PASSWORD)) }
        )
        .navigationTitle(KitStrings.text("Profile"))
        .messageBanner(model.state.message) { model.send(ProfileActionOnMessageShown.shared) }
    }
}

struct ProfileForm: View {
    let state: ProfileState
    let allowPasswordChange: Bool
    let send: (ProfileAction) -> Void
    let onChangePassword: () -> Void
    @SwiftUI.Environment(\.appStyle) private var style

    var body: some View {
        if !state.loaded {
            LoadingView()
        } else {
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.md) {
                    VStack(alignment: .leading, spacing: Spacing.xs) {
                        Text(KitStrings.text("Email")).font(style.typography.label).foregroundStyle(style.colors.onSurfaceVariant)
                        Text(state.email).font(style.typography.body)
                    }
                    .accessibilityElement(children: .combine)
                    SharedTextField(label: KitStrings.text("First name"), text: Binding(get: { state.firstName }, set: { send(ProfileActionOnFirstNameChange(name: $0)) }), error: state.firstNameError)
                        .textContentType(.givenName)
                    SharedTextField(label: KitStrings.text("Last name"), text: Binding(get: { state.lastName }, set: { send(ProfileActionOnLastNameChange(name: $0)) }), error: state.lastNameError)
                        .textContentType(.familyName)
                    SubmitButton(label: KitStrings.text("Save"), enabled: state.canSave, loading: state.isSaving) {
                        send(ProfileActionOnSave.shared)
                    }
                    if allowPasswordChange {
                        Button(KitStrings.text("Change password"), action: onChangePassword).buttonStyle(TextButtonStyle())
                    }
                }
                .padding(style.pack.sizing.screenGutter)
            }
            .scrollDismissesKeyboard(.interactively)
        }
    }
}

#Preview("Profile") {
    PackPreviews {
        ProfileForm(
            state: ProfileState(loaded: true, userId: "1", email: "ada@example.com", firstName: "Ada", lastName: "Lovelace", savedFirstName: "Ada", savedLastName: "Lovelace", firstNameError: nil, lastNameError: nil, isSaving: false, message: nil),
            allowPasswordChange: true, send: { _ in }, onChangePassword: {}
        )
        .frame(height: 520)
    }
}
