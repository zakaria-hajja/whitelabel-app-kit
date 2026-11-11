import SwiftUI

/**
 * The shell's navigation: the selected tab, each tab's stack, and the gate's stack (sign-in, reset
 * password) while signed out. Screens get it from the environment (`@Environment(Navigator.self)`)
 * to push a destination, as Android's `AppNavigator`.
 */
@MainActor
@Observable
public final class Navigator {
    public var selectedTab: String?
    var paths: [String: [Destination]] = [:]
    var gatePath: [Destination] = []

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

    /**
     * Shows an opened link's [destination]: a tab root selects that tab at its root; a gate screen
     * goes on the gate's stack; anything else goes on its feature's [tab] (or the selected one).
     */
    func show(_ destination: Destination, tab: String?, inGate: Bool, visibleTabs: [String]) {
        if inGate {
            gatePath.append(destination)
            return
        }
        if visibleTabs.contains(destination.route) {
            selectedTab = destination.route
            paths[destination.route] = []
            return
        }
        if let tab, visibleTabs.contains(tab) { selectedTab = tab }
        if selectedTab == nil { selectedTab = visibleTabs.first }
        push(destination)
    }

    /** Back to the start, for the next user: on sign-in and sign-out, as Android pops the whole back stack. */
    func reset() {
        selectedTab = nil
        paths = [:]
        gatePath = []
    }

    func path(for tab: String) -> Binding<[Destination]> {
        Binding(get: { self.paths[tab] ?? [] }, set: { self.paths[tab] = $0 })
    }
}
