import SwiftUI
import UIKit

/**
 * The app's two fonts (Android's `brand_display` and `brand_body`), by PostScript name, from the
 * app's bundled font files (`UIAppFonts`). A font that isn't installed falls back to the system
 * font, so a pack never shows a missing glyph. Sizes follow Dynamic Type.
 */
public struct BrandFonts {
    public let displayName: String?
    public let bodyName: String?

    public init(displayName: String?, bodyName: String?) {
        self.displayName = displayName
        self.bodyName = bodyName
    }

    /** From the app's Info.plist: `WLBrandDisplayFont` and `WLBrandBodyFont`. */
    public init(bundle: Bundle = .main) {
        self.init(
            displayName: bundle.object(forInfoDictionaryKey: "WLBrandDisplayFont") as? String,
            bodyName: bundle.object(forInfoDictionaryKey: "WLBrandBodyFont") as? String
        )
    }

    public var hasCustomDisplay: Bool { displayName.map { UIFont(name: $0, size: 12) != nil } ?? false }
    public var hasCustomBody: Bool { bodyName.map { UIFont(name: $0, size: 12) != nil } ?? false }

    func display(size: CGFloat, relativeTo style: Font.TextStyle) -> Font {
        if hasCustomDisplay, let displayName { return .custom(displayName, size: size, relativeTo: style) }
        return .system(style, design: .default)
    }

    func body(size: CGFloat, relativeTo style: Font.TextStyle) -> Font {
        if hasCustomBody, let bodyName { return .custom(bodyName, size: size, relativeTo: style) }
        return .system(style)
    }
}

/** The type roles screens use, built from a pack's tweaks and the brand fonts. */
public struct Typography {
    public let display, headline, title, body, label: Font

    public init(pack: StylePack, fonts: BrandFonts) {
        let titleFont: (CGFloat, Font.TextStyle) -> Font = pack.type.titlesUseBodyFont ? fonts.body : fonts.display
        display = fonts.display(size: 34, relativeTo: .largeTitle).weight(pack.type.displayWeight ?? .bold)
        headline = fonts.display(size: 28, relativeTo: .title).weight(pack.type.headlineWeight ?? .semibold)
        title = titleFont(20, .title3).weight(.semibold)
        body = fonts.body(size: 17, relativeTo: .body)
        label = fonts.body(size: 15, relativeTo: .subheadline).weight(.medium)
    }
}
