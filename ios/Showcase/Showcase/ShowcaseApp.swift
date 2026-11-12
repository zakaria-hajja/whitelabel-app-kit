import SwiftUI
import Shared
import WhitelabelAds
import WhitelabelFirebase
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
        RootView(definition: AppDefinition(style: .material, startTab: HomeRoutes.shared.ROOT, services: [FirebaseService(), AdsService()],
                                              ads: [AdPlacement(name: "home", underTab: HomeRoutes.shared.ROOT)]) {
            Auth()
            Home(shortcuts: [
                HomeShortcut(title: String(localized: "Browse the catalog"), systemImage: "list.bullet", route: CatalogRoutes.shared.LIST),
                HomeShortcut(title: String(localized: "Your profile"), systemImage: "person", route: ProfileRoutes.shared.ROOT),
                HomeShortcut(title: String(localized: "Settings"), systemImage: "gearshape", route: SettingsRoutes.shared.ROOT),
            ])
            // The categories of the backend's seed catalog, as on Android.
            Catalog(categories: ["Books", "Games", "Music", "Travel"])
            Profile()
            Settings(options: SettingsOptions(sections: [
                SettingsSection(title: String(localized: "Firebase")) {
                    PrivacyChoicesRow()
                    RemoteNoteRow()
                },
            ]))
        })
    }
}

/** The note Remote Config carries for Showcase ("default" until a value is fetched, or without Firebase), as on Android. */
private struct RemoteNoteRow: View {
    @State private var note = Self.read()

    var body: some View {
        AppValueRow(String(localized: "Remote note"), value: note)
            .accessibilityIdentifier("showcase.remoteNote")
            .task {
                for await _ in SharedGraph.shared.remoteValues().changes { note = Self.read() }
            }
    }

    private static func read() -> String {
        SharedGraph.shared.remoteValues().string(key: "showcase_remote_note", default: "default")
    }
}
