import CoreGraphics

/** Which of the action dock's extras move to the end of the content; the primary never moves (Kotlin: DockOverflow). */
public enum DockOverflow: Equatable {
    /** Everything stays in the dock. */
    case none
    /** The fine print moves to the content. */
    case finePrint
    /** The fine print and the secondary action move to the content. */
    case finePrintAndSecondary
}

/** The action dock's pure rules, mirrored from Kotlin (`dockOverflow`, `showsDockFade`). */
public enum ActionDockLayout {
    /**
     * Decides which extras leave the dock. A part of height 0 is absent and adds no gap. [chrome] is
     * the dock's top and bottom padding plus its bottom inset; [gap] sits between two present parts.
     * The dock may take [maxFraction] of [available]: over that, the fine print moves first, then the
     * secondary. [compact] (little room above the keyboard) moves both whatever their height.
     */
    static func overflow(
        available: CGFloat, chrome: CGFloat, gap: CGFloat, message: CGFloat, primary: CGFloat,
        secondary: CGFloat, finePrint: CGFloat, maxFraction: CGFloat, compact: Bool
    ) -> DockOverflow {
        guard secondary > 0 || finePrint > 0 else { return .none }
        if compact { return .finePrintAndSecondary }
        let cap = available * maxFraction
        func height(_ parts: CGFloat...) -> CGFloat {
            let present = parts.filter { $0 > 0 }
            return chrome + present.reduce(0, +) + gap * CGFloat(max(present.count - 1, 0))
        }
        if height(message, primary, secondary, finePrint) <= cap { return .none }
        if finePrint > 0 && height(message, primary, secondary) <= cap { return .finePrint }
        return .finePrintAndSecondary
    }

    /** The fade over the content shows only while the content's bottom is below the viewport's (Kotlin: showsDockFade). */
    static func showsFade(contentBottom: CGFloat, viewportBottom: CGFloat) -> Bool {
        contentBottom > viewportBottom + 0.5
    }
}
