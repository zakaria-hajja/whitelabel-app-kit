import SwiftUI

/**
 * A flat (Gallery) page: the list and the navigation bar on the page background, as Android's flat
 * `AppScaffold`. Under Material it changes nothing, so the system's grouped list and translucent
 * bar stay as they are.
 */
public struct FlatPageChrome: ViewModifier {
    @Environment(\.appStyle) private var style

    public init() {}

    static func applies(to pack: StylePack) -> Bool { pack.topBar == .flat }

    public func body(content: Content) -> some View {
        if Self.applies(to: style.pack) {
            content
                .scrollContentBackground(.hidden)
                .background(style.colors.background)
                .toolbarBackground(style.colors.background, for: .navigationBar)
                .toolbarBackground(.visible, for: .navigationBar)
        } else {
            content
        }
    }
}

public extension View {
    /** The page as the style pack draws it: flat on the background (Gallery), or unchanged. */
    func flatPageChrome() -> some View { modifier(FlatPageChrome()) }
}
