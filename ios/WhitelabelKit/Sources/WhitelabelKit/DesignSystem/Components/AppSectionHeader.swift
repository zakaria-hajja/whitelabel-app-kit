import SwiftUI

/**
 * A section's title, as the app's style pack draws them: accent-colored (Material) or a small
 * uppercase caption (Gallery, its text set by `StylePack.sectionCaptionText`), as Android's `AppSectionHeader` (Material keeps the system header). Use it for app screens' sections so
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

    /** The caption's font: the pack's `sectionCaptionText`, or the label role. */
    static func captionFont(_ style: AppStyle) -> Font {
        guard let spec = style.pack.sectionCaptionText else { return style.typography.label }
        let font = spec.font == .display
            ? style.fonts.display(size: spec.size, relativeTo: .caption)
            : style.fonts.body(size: spec.size, relativeTo: .caption)
        return font.weight(spec.weight)
    }

    /** The caption's tracking: the pack's, or 0.12 em of the 11 pt caption size. */
    static func captionTracking(_ pack: StylePack) -> CGFloat { pack.sectionCaptionText?.tracking ?? 0.12 * 11 }

    public var body: some View {
        if Self.restyles(style.pack) {
            Text(text.uppercased())
                .font(Self.captionFont(style))
                .tracking(Self.captionTracking(style.pack))
                .foregroundStyle(style.colors.onSurfaceVariant)
                .textCase(nil) // already uppercased; List would uppercase on its own otherwise
                .accessibilityAddTraits(.isHeader)
        } else {
            // The system list header, exactly as before packs had a say.
            Text(text).accessibilityAddTraits(.isHeader)
        }
    }
}
