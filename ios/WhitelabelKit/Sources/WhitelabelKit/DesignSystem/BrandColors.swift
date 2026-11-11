import SwiftUI

/**
 * The app's colors from its asset catalog, light and dark, by the names that mirror Android's
 * `brand_*` resources (`BrandPrimary` for `brand_light_primary`/`brand_dark_primary`, …). A color
 * the app doesn't define falls back to a system color, as Android falls back to the defaults.
 */
public struct BrandColors {
    public var primary, onPrimary, primaryContainer, onPrimaryContainer: Color
    public var secondary, onSecondary, secondaryContainer, onSecondaryContainer: Color
    public var tertiary, onTertiary, tertiaryContainer, onTertiaryContainer: Color
    public var error, onError: Color
    public var background, onBackground, surface, onSurface, surfaceVariant, onSurfaceVariant, outline, outlineVariant: Color

    /** [prefix] reads a differently named set (the catalog's `GalleryPreview` sample colors). */
    public init(bundle: Bundle = .main, prefix: String = "") {
        func color(_ name: String, _ fallback: Color) -> Color {
            UIColor(named: prefix + name, in: bundle, compatibleWith: nil).map(Color.init(uiColor:)) ?? fallback
        }
        primary = color("BrandPrimary", .accentColor)
        onPrimary = color("BrandOnPrimary", .white)
        primaryContainer = color("BrandPrimaryContainer", Color.accentColor.opacity(0.15))
        onPrimaryContainer = color("BrandOnPrimaryContainer", .primary)
        secondary = color("BrandSecondary", .secondary)
        onSecondary = color("BrandOnSecondary", .white)
        secondaryContainer = color("BrandSecondaryContainer", Color(uiColor: .secondarySystemFill))
        onSecondaryContainer = color("BrandOnSecondaryContainer", .primary)
        tertiary = color("BrandTertiary", .teal)
        onTertiary = color("BrandOnTertiary", .white)
        tertiaryContainer = color("BrandTertiaryContainer", Color.teal.opacity(0.15))
        onTertiaryContainer = color("BrandOnTertiaryContainer", .primary)
        error = color("BrandError", .red)
        onError = color("BrandOnError", .white)
        background = color("BrandBackground", Color(uiColor: .systemBackground))
        onBackground = color("BrandOnBackground", .primary)
        surface = color("BrandSurface", Color(uiColor: .secondarySystemBackground))
        onSurface = color("BrandOnSurface", .primary)
        surfaceVariant = color("BrandSurfaceVariant", Color(uiColor: .tertiarySystemBackground))
        onSurfaceVariant = color("BrandOnSurfaceVariant", .secondary)
        outline = color("BrandOutline", Color(uiColor: .separator))
        outlineVariant = color("BrandOutlineVariant", Color(uiColor: .separator).opacity(0.5))
    }
}
