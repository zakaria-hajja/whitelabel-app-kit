import SwiftUI

/**
 * Everything about an app's look except colors and fonts (those come from the app's assets): type
 * tweaks, corners, how surfaces separate, control sizing, motion and haptics. The values mirror the
 * Kotlin packs in `core/designsystem` (`StylePack.kt`); when they disagree, Kotlin wins.
 */
public struct StylePack: Equatable {
    public var name: String
    public var type: TypeTweaks
    public var corners: Corners
    public var surfaces: SurfaceStyle
    public var sizing: Sizing
    public var motion: MotionSpec
    /** Which feedback each event plays; an event not in the map plays nothing. */
    public var haptics: [HapticEvent: HapticPattern]

    /** This pack with Reduce Motion applied: every movement becomes a short crossfade. */
    public func reduced() -> StylePack {
        var pack = self
        pack.motion = .reduced
        return pack
    }

    /** Standard iOS styling tinted with the brand: shadowed cards, 48 pt controls, no haptics. */
    public static let material = StylePack(
        name: "Material",
        type: TypeTweaks(displayWeight: nil, headlineWeight: nil, titlesUseBodyFont: false),
        corners: Corners(small: 8, medium: 12, large: 16), // Kotlin reads brand_corner_*; these are its defaults
        surfaces: .shadow,
        sizing: Sizing(controlHeight: 48, listRowMinHeight: 56, screenGutter: 16), // Kotlin: StylePack.Material.sizing
        motion: .material,
        haptics: [:]
    )

    /** Editorial and quiet: small radii, hairlines instead of shadows, roomier controls, soft motion. */
    public static let gallery = StylePack(
        name: "Gallery",
        type: TypeTweaks(displayWeight: .regular, headlineWeight: .regular, titlesUseBodyFont: true), // Kotlin: StylePack.Gallery.type
        corners: Corners(small: 4, medium: 8, large: 14), // Kotlin: StylePack.Gallery.corners
        surfaces: .hairline,
        sizing: Sizing(controlHeight: 52, listRowMinHeight: 72, screenGutter: 20), // Kotlin: StylePack.Gallery.sizing
        motion: .gallery,
        haptics: [.confirm: .tick, .success: .confirm, .toggle: .toggle] // Kotlin: StylePack.Gallery.haptics
    )
}

/** Changes to the type roles; the fonts themselves come from the app's brand fonts. */
public struct TypeTweaks: Equatable {
    public var displayWeight: Font.Weight?
    public var headlineWeight: Font.Weight?
    /** Title styles use the body font instead of the display font. */
    public var titlesUseBodyFont: Bool
}

public struct Corners: Equatable {
    public var small: CGFloat
    public var medium: CGFloat
    public var large: CGFloat
}

/** How containers separate from the background: soft shadows, or 1 pt hairline outlines. */
public enum SurfaceStyle: Equatable { case shadow, hairline }

public struct Sizing: Equatable {
    public var controlHeight: CGFloat
    public var listRowMinHeight: CGFloat
    public var screenGutter: CGFloat
}

/** Moments that may give haptic feedback. */
public enum HapticEvent: Hashable { case confirm, success, toggle }

public enum HapticPattern: Equatable { case tick, confirm, toggle }

/** Spacing steps, as `core/designsystem`'s `Spacing`. */
public enum Spacing {
    public static let xs: CGFloat = 4
    public static let sm: CGFloat = 8
    public static let md: CGFloat = 16
    public static let lg: CGFloat = 24
    public static let xl: CGFloat = 32
}

/** How a pack's buttons are shaped: Gallery's small radius (design.html `.btn`), Material's pill (Material 3's default). */
public enum ButtonShape: Equatable {
    case rounded(CGFloat)
    case capsule
}

public extension StylePack {
    var buttonShape: ButtonShape { surfaces == .hairline ? .rounded(corners.small) : .capsule }
}

/**
 * A pack's feedback for screens to play at the moment it applies, as Android's `haptics(event)`:
 * an event the pack doesn't map plays nothing (Material maps none).
 */
public struct Haptics {
    public let pack: StylePack

    public func feedback(for event: HapticEvent) -> SensoryFeedback? {
        switch pack.haptics[event] {
        case .tick: return .selection
        case .confirm: return .success
        case .toggle: return .impact(weight: .light)
        case nil: return nil
        }
    }

    /** Plays [event]'s feedback now (for actions; for state changes prefer `.sensoryFeedback`). */
    @MainActor
    public func play(_ event: HapticEvent) {
        switch pack.haptics[event] {
        case .tick: UISelectionFeedbackGenerator().selectionChanged()
        case .confirm: UINotificationFeedbackGenerator().notificationOccurred(.success)
        case .toggle: UIImpactFeedbackGenerator(style: .light).impactOccurred()
        case nil: break
        }
    }
}
