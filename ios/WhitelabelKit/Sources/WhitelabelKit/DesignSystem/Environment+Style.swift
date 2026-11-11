import SwiftUI

/** The app's look, available to every view: its pack, brand colors and type (set once by the shell). */
public struct AppStyle {
    public var pack: StylePack
    public var colors: BrandColors
    public var fonts: BrandFonts
    public var typography: Typography { Typography(pack: pack, fonts: fonts) }

    public init(pack: StylePack, colors: BrandColors = BrandColors(), fonts: BrandFonts = BrandFonts()) {
        self.pack = pack
        self.colors = colors
        self.fonts = fonts
    }
}

private struct AppStyleKey: EnvironmentKey {
    static let defaultValue = AppStyle(pack: .material, colors: BrandColors(), fonts: BrandFonts(displayName: nil, bodyName: nil))
}

public extension EnvironmentValues {
    var appStyle: AppStyle {
        get { self[AppStyleKey.self] }
        set { self[AppStyleKey.self] = newValue }
    }
}

public extension View {
    /** Applies [style] to this view tree; Reduce Motion swaps in the pack's reduced motion. */
    func appStyle(_ style: AppStyle) -> some View { modifier(AppStyleModifier(style: style)) }
}

private struct AppStyleModifier: ViewModifier {
    let style: AppStyle
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func body(content: Content) -> some View {
        var applied = style
        if reduceMotion { applied.pack = style.pack.reduced() }
        return content.environment(\.appStyle, applied).tint(style.colors.primary)
    }
}
