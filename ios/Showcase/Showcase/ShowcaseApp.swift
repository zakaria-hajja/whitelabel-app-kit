import SwiftUI
import Shared
import WhitelabelKit

/** Showcase on iOS: the foundation's features on the Material pack. In Debug, `-designCatalog` opens the design-system catalog instead. */
@main struct ShowcaseApp: App {
    var body: some Scene {
        WindowGroup {
            #if DEBUG
            if ProcessInfo.processInfo.arguments.contains("-designCatalog") {
                DesignSystemCatalog(dark: ProcessInfo.processInfo.arguments.contains("-catalogDark"))
            } else {
                root
            }
            #else
            root
            #endif
        }
    }

    private var root: some View {
        RootView(definition: AppDefinition(style: .material, startTab: HomeRoutes.shared.ROOT) {
            Auth()
            Home(shortcuts: [
                HomeShortcut(title: String(localized: "Browse the catalog"), systemImage: "list.bullet", route: CatalogRoutes.shared.LIST),
                HomeShortcut(title: String(localized: "Your profile"), systemImage: "person", route: ProfileRoutes.shared.ROOT),
                HomeShortcut(title: String(localized: "Settings"), systemImage: "gearshape", route: SettingsRoutes.shared.ROOT),
            ])
            Profile()
            Settings()
        })
    }
}
