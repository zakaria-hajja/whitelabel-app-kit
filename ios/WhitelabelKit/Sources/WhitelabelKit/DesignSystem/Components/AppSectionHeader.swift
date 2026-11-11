import SwiftUI

/**
 * A section's title, as the app's style pack draws them: accent-colored (Material) or a small
 * uppercase caption (Gallery), as Android's `AppSectionHeader`. Use it for app screens' sections so
 * they match the shared ones.
 */
public struct AppSectionHeader: View {
    @Environment(\.appStyle) private var style
    private let text: String

    public init(_ text: String) {
        self.text = text
    }

    public var body: some View {
        Group {
            switch style.pack.sectionHeader {
            case .accent:
                Text(text).font(style.typography.title).foregroundStyle(style.colors.primary)
            case .caption:
                Text(text.uppercased())
                    .font(style.typography.label)
                    .tracking(0.12 * 11) // 0.12 em of the caption size
                    .foregroundStyle(style.colors.onSurfaceVariant)
            }
        }
        .textCase(nil) // List section headers uppercase on their own; the pack decides.
        .accessibilityAddTraits(.isHeader)
    }
}
