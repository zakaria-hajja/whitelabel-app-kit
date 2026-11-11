import SwiftUI

/**
 * The shell's navigation: the selected tab and each tab's stack. Screens get it from the
 * environment (`@SwiftUI.Environment(Navigator.self)`) to push a destination, as Android's `AppNavigator`.
 */
@MainActor
@Observable
public final class Navigator {
    public var selectedTab: String?
    var paths: [String: [Destination]] = [:]

    public init() {}

    /** Pushes [destination] onto the selected tab's stack. */
    public func push(_ destination: Destination) {
        guard let tab = selectedTab else { return }
        paths[tab, default: []].append(destination)
    }

    /** Goes back one screen in the selected tab. */
    public func pop() {
        guard let tab = selectedTab, paths[tab]?.isEmpty == false else { return }
        paths[tab]?.removeLast()
    }

    func path(for tab: String) -> Binding<[Destination]> {
        Binding(get: { self.paths[tab] ?? [] }, set: { self.paths[tab] = $0 })
    }
}
