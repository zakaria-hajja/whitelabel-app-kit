import SwiftUI

/**
 * A section's title, as the app's style pack draws them: accent-colored (Material) or a small
 * uppercase caption (Gallery), as Android's `AppSectionHeader` (Material keeps the system header). Use it for app screens' sections so
 * they match the shared ones.
 */
public struct AppSectionHeader: View {
    @Environment(\.appStyle) private var style
    private let text: String

    public init(_ text: String) {
        self.text = text
    }

    /** Whether the pack restyles section titles; Material leaves the system list header as it is. */
    static func restyles(_ pack: StylePack) -> Bool { pack.sectionHeader == .caption }

    public var body: some View {
        if Self.restyles(style.pack) {
            Text(text.uppercased())
                .font(style.typography.label)
                .tracking(0.12 * 11) // 0.12 em of the caption size
                .foregroundStyle(style.colors.onSurfaceVariant)
                .textCase(nil) // already uppercased; List would uppercase on its own otherwise
                .accessibilityAddTraits(.isHeader)
        } else {
            // The system list header, exactly as before packs had a say.
            Text(text).accessibilityAddTraits(.isHeader)
        }
    }
}
