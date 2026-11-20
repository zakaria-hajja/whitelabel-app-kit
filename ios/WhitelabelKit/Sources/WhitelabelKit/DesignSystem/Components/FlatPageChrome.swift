import SwiftUI

/**
 * A flat (Gallery) page: the list and the navigation bar on the page background, as Android's flat
 * `AppScaffold`. Under Material it changes nothing, so the system's grouped list and translucent
 * bar stay as they are.
 */
public struct FlatPageChrome: ViewModifier {
    @Environment(\.appStyle) private var style
    private let largeTitle: Bool

    /**
     * [largeTitle]: the page shows a large navigation title (a tab's root). Forcing the bar's background
     * visible hides a large title at rest on iOS 26 (it only appears once the page scrolls), so such a
     * page leaves the background to the system: transparent over the page at rest, the page colour once
     * content scrolls under it.
     */
    public init(largeTitle: Bool = false) {
        self.largeTitle = largeTitle
    }

    static func applies(to pack: StylePack) -> Bool { pack.topBar == .flat }

    public func body(content: Content) -> some View {
        if Self.applies(to: style.pack) {
            content
                .scrollContentBackground(.hidden)
                .background(style.colors.background)
                .toolbarBackground(style.colors.background, for: .navigationBar)
                .toolbarBackground(largeTitle ? .automatic : .visible, for: .navigationBar)
        } else {
            content
        }
    }
}

public extension View {
    /**
     * The page as the style pack draws it: flat on the background (Gallery), or unchanged. Pass
     * [largeTitle] true on a page with a large navigation title, so the title shows at rest.
     */
    func flatPageChrome(largeTitle: Bool = false) -> some View { modifier(FlatPageChrome(largeTitle: largeTitle)) }
}
