import SwiftUI

/**
 * A settings row that opens something (a link, another screen), as Android's `SettingsLinkRow`: the
 * style of About's Privacy policy and Terms of use, so an app's own rows (e.g. its open-source
 * licences in `SettingsOptions(aboutRows:)`) look the same.
 */
public struct SettingsLinkRow: View {
    let label: String
    let action: () -> Void

    @Environment(\.appStyle) private var style

    public init(_ label: String, action: @escaping () -> Void) {
        self.label = label
        self.action = action
    }

    public var body: some View {
        Button(label, action: action)
            .font(style.typography.body)
            .foregroundStyle(style.colors.primary)
    }
}
