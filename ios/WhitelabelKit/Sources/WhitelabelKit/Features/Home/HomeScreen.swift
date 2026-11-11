import SwiftUI
import Shared

/** The greeting (the cached user), the shortcuts, and pull to refresh on the shared HomeViewModel. */
struct HomeScreen: View {
    let shortcuts: [HomeShortcut]

    @State private var screen = ScreenModel {
        let viewModel = SharedGraph.shared.homeViewModel()
        return SharedModel<HomeState, HomeAction>(holder: ViewModelHolder(viewModel: viewModel), state: viewModel.state, onAction: viewModel.onAction)
    }
    @SwiftUI.Environment(Navigator.self) private var navigator: Navigator?
    @SwiftUI.Environment(\.appBrand) private var brand

    var body: some View {
        let model = screen.model
        HomeContent(
            state: model.state,
            shortcuts: shortcuts.filter { navigator?.canOpenTab($0.route) ?? false },
            onOpenTab: { navigator?.openTab($0) }
        )
        .refreshable {
            model.send(HomeActionOnRefresh.shared)
            // Keep the spinner until the shared refresh ends.
            while true {
                try? await Task.sleep(nanoseconds: 100_000_000)
                if !model.state.isRefreshing || Task.isCancelled { break }
            }
        }
        .navigationTitle(brand?.appName ?? "")
        .messageBanner(model.state.message) { model.send(HomeActionOnMessageShown.shared) }
    }
}

struct HomeContent: View {
    let state: HomeState
    let shortcuts: [HomeShortcut]
    let onOpenTab: (String) -> Void
    @SwiftUI.Environment(\.appStyle) private var style

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.md) {
                UiTextView(state.greeting)
                    .font(style.typography.headline)
                    .foregroundStyle(style.colors.onBackground)
                    .accessibilityAddTraits(.isHeader)
                if !shortcuts.isEmpty {
                    Text(KitStrings.text("Shortcuts"))
                        .font(style.typography.label)
                        .foregroundStyle(style.colors.primary)
                    ForEach(shortcuts, id: \.route) { shortcut in
                        Button { onOpenTab(shortcut.route) } label: {
                            AppCard {
                                Label(shortcut.title, systemImage: shortcut.systemImage)
                                    .font(style.typography.title)
                                    .foregroundStyle(style.colors.onSurface)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            }
                        }
                        .buttonStyle(.plain)
                        .accessibilityIdentifier("shortcut.\(shortcut.route)")
                    }
                }
            }
            .padding(style.pack.sizing.screenGutter)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .background(style.colors.background)
    }
}

#Preview("Home") {
    PackPreviews {
        HomeContent(
            state: HomeState(greeting: UiTextDynamic(value: "Hello, Ada"), isRefreshing: false, message: nil),
            shortcuts: [HomeShortcut(title: "Your profile", systemImage: "person", route: "profile"), HomeShortcut(title: "Settings", systemImage: "gearshape", route: "settings")],
            onOpenTab: { _ in }
        )
        .frame(height: 360)
    }
}
