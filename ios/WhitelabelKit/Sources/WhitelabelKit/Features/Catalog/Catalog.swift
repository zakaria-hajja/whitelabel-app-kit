import SwiftUI
import Shared

/**
 * Adds the catalog tab: a searchable, filterable, sortable, paginated list and an item's detail,
 * as Android's `catalog()`. [categories] become filter chips (none: no chips). The options are
 * checked in Kotlin (`CatalogOptions`): an invalid one stops the app at launch, as on Android.
 * Its flag key is `catalog`; the detail opens from `<scheme>://catalog/<id>`. Needs `Auth()`.
 */
public struct Catalog: Feature {
    private let options: CatalogOptions

    public init(categories: [String] = [], pageSize: Int32 = 20, defaultSort: CatalogSort = .newest) {
        options = CatalogOptions(categories: categories, pageSize: pageSize, defaultSort: defaultSort)
    }

    public var spec: FeatureSpec {
        FeatureSpec(id: "catalog", flagKey: "catalog", tabRoute: CatalogRoutes.shared.LIST, requiresSignIn: true, isGate: false, requiresBackend: false)
    }

    public func tab() -> TabItem? {
        TabItem(route: CatalogRoutes.shared.LIST, title: KitStrings.text("Catalog"), systemImage: "list.bullet")
    }

    @MainActor public func routes(_ route: String, _ args: [String: String]) -> AnyView? {
        switch route {
        case CatalogRoutes.shared.LIST: AnyView(CatalogListScreen(options: options))
        case CatalogRoutes.shared.DETAIL: AnyView(CatalogDetailScreen(id: args["id"] ?? ""))
        default: nil
        }
    }

    /** `<scheme>://catalog/<id>`. */
    public func destination(forLink url: URL) -> Destination? {
        guard url.host?.lowercased() == "catalog", let id = url.pathComponents.dropFirst().first, !id.isEmpty else { return nil }
        return Destination(CatalogRoutes.shared.DETAIL, args: ["id": id])
    }
}
