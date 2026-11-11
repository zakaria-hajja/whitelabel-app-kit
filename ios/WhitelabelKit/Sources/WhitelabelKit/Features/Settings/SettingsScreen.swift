import SwiftUI
import Shared

/**
 * Theme, language, notifications (signed in), about, legal links and sign out, on the shared
 * SettingsViewModel, as Android's settings screen. The diagnostics row is Android-only.
 */
struct SettingsScreen: View {
    let options: SettingsOptions

    @State private var screen = ScreenModel {
        let viewModel = SharedGraph.shared.settingsViewModel()
        return SharedModel<SettingsState, SettingsAction>(holder: ViewModelHolder(viewModel: viewModel), state: viewModel.state, onAction: viewModel.onAction)
    }
    @State private var sheet: Sheet?
    @SwiftUI.Environment(\.openURL) private var openURL
    @SwiftUI.Environment(\.scenePhase) private var scenePhase
    @SwiftUI.Environment(\.appStyle) private var style

    private enum Sheet: Identifiable {
        case theme, language
        var id: Self { self }
    }

    private var model: SharedModel<SettingsState, SettingsAction> { screen.model }
    private var state: SettingsState { model.state }
    private var languages: [String] { SettingsViewModelKt.offeredLanguages(options: options.languages, remote: state.remoteLanguages) }

    var body: some View {
        SettingsList(
            state: state,
            options: options,
            languages: languages,
            send: model.send,
            onChoose: { sheet = $0 == .theme ? .theme : .language },
            onOpen: open
        )
        .navigationTitle(KitStrings.text("Settings"))
        .sheet(item: $sheet) { sheet in
            switch sheet {
            case .theme:
                AppChoiceSheet(title: KitStrings.text("Theme"), choices: SettingsList.themes, selected: state.theme, label: SettingsList.themeLabel) {
                    model.send(SettingsActionOnThemeSelect(theme: $0))
                }
            case .language:
                AppChoiceSheet(title: KitStrings.text("Language"), choices: [nil] + languages.map { Optional($0) }, selected: .some(state.language), label: SettingsList.languageLabel) {
                    model.send(SettingsActionOnLanguageSelect(languageTag: $0))
                }
            }
        }
        .appConfirm(
            KitStrings.text("Sign out?"),
            isPresented: Binding(get: { state.confirmSignOut }, set: { if !$0 { model.send(SettingsActionOnSignOutDismiss.shared) } }),
            confirmLabel: KitStrings.text("Sign out"),
            message: String(format: KitStrings.text("You'll need to sign in again to use %@."), state.appName)
        ) {
            model.send(SettingsActionOnSignOutConfirm.shared)
        }
        .messageBanner(state.message) { model.send(SettingsActionOnMessageShown.shared) }
        // The language may have been changed in the system's settings for the app while away.
        .onAppear { model.send(SettingsActionOnScreenResumed.shared) }
        .onChange(of: scenePhase) { if scenePhase == .active { model.send(SettingsActionOnScreenResumed.shared) } }
    }

    private func open(_ link: String) {
        if let url = URL(string: link) { openURL(url) }
    }
}

/** The settings rows for a state, without the shared model, so previews show every state. */
struct SettingsList: View {
    enum Choice { case theme, language }

    let state: SettingsState
    let options: SettingsOptions
    let languages: [String]
    let send: (SettingsAction) -> Void
    let onChoose: (Choice) -> Void
    let onOpen: (String) -> Void

    @SwiftUI.Environment(\.appStyle) private var style

    var body: some View {
        List {
            ForEach(options.sections) { section in
                Section {
                    section.content()
                } header: {
                    if let title = section.title { Text(title) }
                }
            }
            Section(KitStrings.text("Appearance")) {
                AppValueRow(KitStrings.text("Theme"), value: Self.themeLabel(state.theme)) { onChoose(.theme) }
                if !languages.isEmpty {
                    AppValueRow(KitStrings.text("Language"), value: Self.languageLabel(state.language)) { onChoose(.language) }
                    if let note = state.languageNote {
                        UiTextView(note)
                            .font(style.typography.label)
                            .foregroundStyle(style.colors.onSurfaceVariant)
                    }
                }
            }
            if options.showNotifications && state.signedIn {
                Section(KitStrings.text("Notifications")) { notifications }
            }
            Section(KitStrings.text("About")) {
                Text(String(format: KitStrings.text("Version %@"), state.versionName))
                    .font(style.typography.body)
                if let note = options.aboutNote {
                    Text(note).font(style.typography.body).foregroundStyle(style.colors.onSurfaceVariant)
                }
                if let email = state.supportEmail {
                    link(KitStrings.text("Contact support")) { onOpen("mailto:\(email)") }
                }
                link(KitStrings.text("Privacy policy")) { onOpen(state.privacyUrl) }
                link(KitStrings.text("Terms of use")) { onOpen(state.termsUrl) }
            }
            if state.signedIn {
                Section(KitStrings.text("Account")) {
                    Button(KitStrings.text("Sign out")) { send(SettingsActionOnSignOutClick.shared) }
                        .buttonStyle(SecondaryButtonStyle())
                        .listRowSeparator(.hidden)
                }
            }
        }
    }

    @ViewBuilder private var notifications: some View {
        if let settings = state.notifications {
            toggle(KitStrings.text("Push notifications"), settings.push, .push)
            toggle(KitStrings.text("Email updates"), settings.email, .email)
            toggle(KitStrings.text("News and offers"), settings.marketing, .marketing)
        } else if let error = state.notificationsError {
            SharedErrorRow(message: error) { send(SettingsActionOnRetryNotifications.shared) }
        } else {
            ProgressView().frame(maxWidth: .infinity)
        }
    }

    private func toggle(_ label: String, _ isOn: Bool, _ kind: NotificationKind) -> some View {
        AppToggleRow(label, isOn: Binding(get: { isOn }, set: { send(SettingsActionOnNotificationToggle(kind: kind, enabled: $0)) }))
    }

    private func link(_ label: String, action: @escaping () -> Void) -> some View {
        Button(label, action: action)
            .font(style.typography.body)
            .foregroundStyle(style.colors.primary)
    }

    static let themes: [ThemePreference] = [.system, .light, .dark]

    static func themeLabel(_ theme: ThemePreference) -> String {
        switch theme {
        case .light: KitStrings.text("Light")
        case .dark: KitStrings.text("Dark")
        default: KitStrings.text("System default")
        }
    }

    /** A language's own name ("Français"), so it reads right whatever the current language; nil follows the device. */
    static func languageLabel(_ tag: String?) -> String {
        guard let tag else { return KitStrings.text("Device language") }
        let locale = Locale(identifier: tag)
        return (locale.localizedString(forLanguageCode: tag) ?? tag).capitalized(with: locale)
    }
}

/** A shared error message with a retry, inside a list. */
private struct SharedErrorRow: View {
    let message: UiText
    let onRetry: () -> Void
    @State private var text = ""

    var body: some View {
        ErrorStateView(message: text, onRetry: onRetry)
            .task(id: ObjectIdentifier(message)) { text = await resolve(message) }
    }
}

private func previewState(signedIn: Bool, note: Bool = false) -> SettingsState {
    SettingsState(
        theme: .dark, language: note ? "fr" : nil, remoteLanguages: [], signedIn: signedIn,
        notifications: signedIn ? NotificationSettings(push: true, email: false, marketing: false) : nil,
        notificationsError: nil, message: nil, confirmSignOut: false, appName: "Sample", versionName: "1.0.0",
        supportEmail: "help@sample.example", privacyUrl: "https://p", termsUrl: "https://t", showDiagnostics: false,
        languageAppliesOnRestart: note
    )
}

#Preview("Signed in") {
    PackPreviews {
        SettingsList(state: previewState(signedIn: true), options: .init(), languages: ["en", "fr"], send: { _ in }, onChoose: { _ in }, onOpen: { _ in })
            .frame(height: 700)
    }
}

#Preview("Signed out, language note") {
    PackPreviews {
        SettingsList(state: previewState(signedIn: false, note: true), options: .init(), languages: ["en", "fr"], send: { _ in }, onChoose: { _ in }, onOpen: { _ in })
            .frame(height: 500)
    }
}
