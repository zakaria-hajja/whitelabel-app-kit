import SwiftUI
import Shared

/** The scrolling column every sign-in screen uses: a title, then the form. */
struct AuthForm<Content: View>: View {
    let title: String
    @ViewBuilder let content: Content
    @SwiftUI.Environment(\.appStyle) private var style

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.md) {
                Text(title)
                    .font(style.typography.headline)
                    .foregroundStyle(style.colors.onBackground)
                    .accessibilityAddTraits(.isHeader)
                content
            }
            .padding(style.pack.sizing.screenGutter)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(style.colors.background)
        .navigationBarTitleDisplayMode(.inline)
    }
}

/** A text field on a shared state's text, whose error is a shared message. */
struct SharedTextField: View {
    let label: String
    @Binding var text: String
    var error: UiText?
    var isSecure = false
    @State private var resolved: String?
    @State private var local = ""

    var body: some View {
        AppTextField(label, text: $local, error: error == nil ? nil : resolved, isSecure: isSecure)
            .bufferedText(text, local: $local) { text = $0 }
            .task(id: error.map(ObjectIdentifier.init)) { resolved = if let error { await resolve(error) } else { nil } }
    }
}

/** A form-level shared error under the fields. */
struct FormError: View {
    let error: UiText?
    @SwiftUI.Environment(\.appStyle) private var style

    var body: some View {
        if let error {
            UiTextView(error).font(style.typography.body).foregroundStyle(style.colors.error)
        }
    }
}

/** The form's main action, with a spinner while it runs. */
struct SubmitButton: View {
    let label: String
    var enabled = true
    var loading = false
    let action: () -> Void

    var body: some View {
        Button {
            // The form is sent: put the keyboard away (it would cover the result and the tab bar).
            UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
            action()
        } label: {
            HStack(spacing: Spacing.sm) {
                if loading { ProgressView().tint(.white) }
                Text(label)
            }
            .frame(maxWidth: .infinity)
        }
        .buttonStyle(PrimaryButtonStyle())
        .disabled(!enabled || loading)
    }
}

extension View {
    /** An email field's keyboard, without autocorrection or capitals. */
    func emailInput() -> some View {
        keyboardType(.emailAddress).textContentType(.emailAddress).textInputAutocapitalization(.never).autocorrectionDisabled()
    }
}
