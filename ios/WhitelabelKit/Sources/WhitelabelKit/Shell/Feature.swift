import SwiftUI
import Shared

/** A screen to show: a shared route id (`<Name>Routes` constants) and its arguments (an item id, a token). */
public struct Destination: Hashable {
    public let route: String
    public let args: [String: String]

    public init(_ route: String, args: [String: String] = [:]) {
        self.route = route
        self.args = args
    }
}

/** A feature's tab: its root route, its title (already localized) and an SF Symbol. */
public struct TabItem {
    public let route: String
    public let title: String
    public let systemImage: String

    public init(route: String, title: String, systemImage: String) {
        self.route = route
        self.title = title
        self.systemImage = systemImage
    }
}

/**
 * One feature of an iOS app, the Swift side of Android's `FeatureEntry`. [spec] is what the shared
 * shell model decides on (flags, tabs, sign-in); the rest builds its screens. An app's own feature
 * implements this too.
 */
public protocol Feature {
    var spec: FeatureSpec { get }

    /** The feature's tab, or nil when it has none. */
    func tab() -> TabItem?

    /** The screen for [route] if this feature owns it, else nil. Its tab root is called with its tab's route. */
    @MainActor func routes(_ route: String, _ args: [String: String]) -> AnyView?

    /** Where a deep link of the app's scheme leads, if this feature owns it. */
    func destination(forLink url: URL) -> Destination?

    /** The gate's screen while signed out (sign-in), for the feature with `spec.isGate`. */
    @MainActor func gateRoot() -> AnyView?
}

public extension Feature {
    func destination(forLink url: URL) -> Destination? { nil }
    @MainActor func gateRoot() -> AnyView? { nil }
}

/** Lists an app's features: `AppDefinition(style: .gallery) { Auth(); Home(); Settings() }`. */
@resultBuilder
public enum FeatureBuilder {
    public static func buildExpression(_ feature: any Feature) -> [any Feature] { [feature] }
    public static func buildBlock(_ parts: [any Feature]...) -> [any Feature] { parts.flatMap { $0 } }
    public static func buildOptional(_ part: [any Feature]?) -> [any Feature] { part ?? [] }
    public static func buildEither(first part: [any Feature]) -> [any Feature] { part }
    public static func buildEither(second part: [any Feature]) -> [any Feature] { part }
    public static func buildArray(_ parts: [[any Feature]]) -> [any Feature] { parts.flatMap { $0 } }
}

private struct AppBrandKey: EnvironmentKey {
    static let defaultValue: BrandConfig? = nil
}

public extension EnvironmentValues {
    /** The app's identity (name, scheme, links), set by `RootView`. */
    var appBrand: BrandConfig? {
        get { self[AppBrandKey.self] }
        set { self[AppBrandKey.self] = newValue }
    }
}
