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

/** One type role's size (scaled with Dynamic Type) and weight. */
public struct TypeRole: Equatable {
    public var size: CGFloat
    public var weight: Font.Weight
}

/**
 * The sizes and weights of a pack's type roles. Gallery follows Munch's `docs/design/tokens.json`
 * (the Kotlin pack defines no sizes); Material keeps iOS's own scale with Material 3's regular
 * display weights.
 */
public struct TypeScale: Equatable {
    public var display, headline, title, body, label: TypeRole

    public static func `for`(_ pack: StylePack) -> TypeScale {
        pack.name == StylePack.gallery.name
            ? TypeScale(
                display: TypeRole(size: 52, weight: .regular),
                headline: TypeRole(size: 26, weight: .regular),
                title: TypeRole(size: 18, weight: .semibold),
                body: TypeRole(size: 16, weight: .regular),
                label: TypeRole(size: 14, weight: .semibold)
            )
            : TypeScale(
                display: TypeRole(size: 34, weight: .regular),
                headline: TypeRole(size: 28, weight: .regular),
                title: TypeRole(size: 20, weight: .semibold),
                body: TypeRole(size: 17, weight: .regular),
                label: TypeRole(size: 15, weight: .medium)
            )
    }
}

/** The type roles screens use, built from a pack's scale, its tweaks and the brand fonts. */
public struct Typography {
    public let display, headline, title, body, label: Font

    public init(pack: StylePack, fonts: BrandFonts) {
        let scale = TypeScale.for(pack)
        let titleFont: (CGFloat, Font.TextStyle) -> Font = pack.type.titlesUseBodyFont ? fonts.body : fonts.display
        display = fonts.display(size: scale.display.size, relativeTo: .largeTitle).weight(pack.type.displayWeight ?? scale.display.weight)
        headline = fonts.display(size: scale.headline.size, relativeTo: .title).weight(pack.type.headlineWeight ?? scale.headline.weight)
        title = titleFont(scale.title.size, .title3).weight(scale.title.weight)
        body = fonts.body(size: scale.body.size, relativeTo: .body).weight(scale.body.weight)
        label = fonts.body(size: scale.label.size, relativeTo: .subheadline).weight(scale.label.weight)
    }
}
