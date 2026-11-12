import SwiftUI
import Shared

/** The visible tabs in order, each a native navigation stack starting at its feature's root. */
struct TabsView: View {
    let definition: AppDefinition
    let routes: [String]
    @Bindable var navigator: Navigator

    var body: some View {
        TabView(selection: $navigator.selectedTab) {
            ForEach(routes, id: \.self) { route in
                if let feature = definition.feature(forTab: route), let tab = feature.tab() {
                    NavigationStack(path: navigator.path(for: route)) {
                        (feature.routes(route, [:]) ?? AnyView(EmptyView()))
                            .navigationDestination(for: Destination.self) { destination in
                                definition.view(for: destination) ?? AnyView(EmptyView())
                            }
                    }
                    .tabItem { Label(tab.title, systemImage: tab.systemImage) }
                    .tag(Optional(route))
                }
            }
        }
        .onAppear(perform: selectStartTab)
        .onChange(of: routes) { selectStartTab() }
        // Signing in or out resets the navigator: pick the start tab again rather than leave none selected.
        .onChange(of: navigator.selectedTab) { if navigator.selectedTab == nil { selectStartTab() } }
    }

    /** The definition's start tab (or the first), unless a visible tab is already selected. */
    private func selectStartTab() {
        navigator.visibleTabs = routes
        if let selected = navigator.selectedTab, routes.contains(selected) { return }
        navigator.selectedTab = definition.spec.resolveStartTab(visible: routes)
    }
}
