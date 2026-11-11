import SwiftUI

/**
 * An app's own settings section, above the shared ones in list order, as Android's
 * `SettingsSection`. A nil [title] draws no header (for a callout). Build its rows from
 * `AppValueRow`, `AppToggleRow` and `AppChoiceSheet`.
 */
public struct SettingsSection: Identifiable {
    public let id = UUID()
    public let title: String?
    let content: () -> AnyView

    public init<Content: View>(title: String?, @ViewBuilder content: @escaping () -> Content) {
        self.title = title
        self.content = { AnyView(content()) }
    }
}
