import SwiftUI

/** A cubic-bezier easing curve, as in CSS `cubic-bezier(a, b, c, d)`. */
public struct CubicBezier: Equatable {
    public var a: Double, b: Double, c: Double, d: Double
    public init(_ a: Double, _ b: Double, _ c: Double, _ d: Double) { (self.a, self.b, self.c, self.d) = (a, b, c, d) }
}

/** One animation curve: a timed tween or a physics spring (Kotlin: `MotionCurve`). */
public enum MotionCurve: Equatable {
    case tween(milliseconds: Int, CubicBezier)
    case spring(dampingRatio: Double, stiffness: Double)

    /** SwiftUI's spring response: the period of the undamped spring, 2π/√stiffness at unit mass. */
    var springResponse: Double {
        guard case let .spring(_, stiffness) = self else { return 0 }
        return 2 * .pi / stiffness.squareRoot()
    }

    public var animation: Animation {
        switch self {
        case let .tween(milliseconds, curve):
            return .timingCurve(curve.a, curve.b, curve.c, curve.d, duration: Double(milliseconds) / 1000)
        case let .spring(dampingRatio, _):
            return .spring(response: springResponse, dampingFraction: dampingRatio)
        }
    }
}

/**
 * A pack's named motion (Kotlin: `MotionSpec`): pushes and pops, shared elements, the tab switch
 * (with its scale), small emphasis, and feedback.
 */
public struct MotionSpec: Equatable {
    public var screenEnter: MotionCurve
    public var screenExit: MotionCurve
    public var sharedElement: MotionCurve
    public var tabSwitch: MotionCurve
    public var tabSwitchScale: Double
    public var emphasis: MotionCurve
    public var snackbar: MotionCurve

    private static let fastOutSlowIn = CubicBezier(0.4, 0, 0.2, 1)
    private static let standard = CubicBezier(0.2, 0, 0, 1)
    private static let emphasized = CubicBezier(0.2, 0, 0, 1)
    private static let emphasizedAccelerate = CubicBezier(0.3, 0, 0.8, 0.15)

    // Kotlin: MotionSpec.Material
    public static let material = MotionSpec(
        screenEnter: .tween(milliseconds: 700, fastOutSlowIn),
        screenExit: .tween(milliseconds: 700, fastOutSlowIn),
        sharedElement: .tween(milliseconds: 300, fastOutSlowIn),
        tabSwitch: .tween(milliseconds: 700, fastOutSlowIn),
        tabSwitchScale: 1,
        emphasis: .spring(dampingRatio: 1, stiffness: 400),
        snackbar: .tween(milliseconds: 250, fastOutSlowIn)
    )

    // Kotlin: MotionSpec.Gallery
    public static let gallery = MotionSpec(
        screenEnter: .tween(milliseconds: 420, emphasized),
        screenExit: .tween(milliseconds: 320, emphasizedAccelerate),
        sharedElement: .tween(milliseconds: 420, emphasized),
        tabSwitch: .tween(milliseconds: 210, standard),
        tabSwitchScale: 0.96,
        emphasis: .spring(dampingRatio: 0.55, stiffness: 380),
        snackbar: .tween(milliseconds: 200, standard)
    )

    // Kotlin: MotionSpec.Reduced
    public static let reduced: MotionSpec = {
        let fade = MotionCurve.tween(milliseconds: 150, CubicBezier(0, 0, 1, 1))
        return MotionSpec(screenEnter: fade, screenExit: fade, sharedElement: fade, tabSwitch: fade, tabSwitchScale: 1, emphasis: fade, snackbar: fade)
    }()
}
