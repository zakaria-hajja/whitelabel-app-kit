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
    /** The gate's screens are showing (signed out): push and pop work on its stack. */
    var isGateShowing = false
    /** The visible tabs, as the shared shell model decided them (set by the tab view). */
    public internal(set) var visibleTabs: [String] = []

    public init() {}

    /** Pushes [destination] onto the gate's stack while it shows, else the selected tab's. */
    public func push(_ destination: Destination) {
        if isGateShowing {
            gatePath.append(destination)
            return
        }
        guard let tab = selectedTab else { return }
        paths[tab, default: []].append(destination)
    }

    /** Whether [route] is a visible tab: a shortcut to a missing or switched-off tab is hidden. */
    public func canOpenTab(_ route: String) -> Bool { visibleTabs.contains(route) }

    /** Switches to the tab with root [route], if it is visible, with its stack as it was (Android's `toTab` restores it too). */
    public func openTab(_ route: String) {
        guard canOpenTab(route) else { return }
        selectedTab = route
    }

    /** Goes back one screen. */
    public func pop() {
        if isGateShowing {
            if !gatePath.isEmpty { gatePath.removeLast() }
            return
        }
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
        guard let selected = selectedTab else { return }
        paths[selected, default: []].append(destination)
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
