import SwiftUI

/** The main action: filled with the brand's primary color, at least the pack's control height, wrapping its label. */
public struct PrimaryButtonStyle: ButtonStyle {
    @Environment(\.appStyle) private var style
    @Environment(\.isEnabled) private var isEnabled

    public init() {}

    public func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(style.typography.label)
            .multilineTextAlignment(.center)
            .foregroundStyle(style.colors.onPrimary)
            .padding(.horizontal, Spacing.md)
            .padding(.vertical, Spacing.sm)
            .frame(maxWidth: .infinity, minHeight: style.pack.sizing.controlHeight)
            .background(style.colors.primary.opacity(isEnabled ? 1 : 0.4), in: style.pack.buttonShape.shape)
            .pressEffect(configuration.isPressed, style: style)
    }
}

/** A second action next to a primary one: outlined. */
public struct SecondaryButtonStyle: ButtonStyle {
    @Environment(\.appStyle) private var style

    public init() {}

    public func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(style.typography.label)
            .multilineTextAlignment(.center)
            .foregroundStyle(style.colors.primary)
            .padding(.horizontal, Spacing.md)
            .padding(.vertical, Spacing.sm)
            .frame(maxWidth: .infinity, minHeight: style.pack.sizing.controlHeight)
            .overlay(style.pack.buttonShape.shape.strokeBorder(style.colors.outline))
            .pressEffect(configuration.isPressed, style: style)
    }
}

/** A quiet action inside content (retry, links). */
public struct TextButtonStyle: ButtonStyle {
    @Environment(\.appStyle) private var style

    public init() {}

    public func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(style.typography.label)
            .foregroundStyle(style.colors.primary)
            .padding(.vertical, Spacing.sm)
            .opacity(configuration.isPressed ? 0.6 : 1)
    }
}

extension ButtonShape {
    var shape: AnyInsettableShape {
        switch self {
        case let .rounded(radius): AnyInsettableShape(RoundedRectangle(cornerRadius: radius))
        case .capsule: AnyInsettableShape(Capsule())
        }
    }
}

/** A type-erased insettable shape, so a button can be rounded or a capsule from the pack. */
struct AnyInsettableShape: InsettableShape {
    private let path: (CGRect) -> Path
    private let insetBy: (CGFloat) -> AnyInsettableShape

    init<S: InsettableShape>(_ shape: S) {
        path = { shape.path(in: $0) }
        insetBy = { AnyInsettableShape(shape.inset(by: $0)) }
    }

    func path(in rect: CGRect) -> Path { path(rect) }
    func inset(by amount: CGFloat) -> AnyInsettableShape { insetBy(amount) }
}

extension View {
    /** The pack's emphasis motion on press (Gallery's soft spring; Reduce Motion: a fade). */
    func pressEffect(_ pressed: Bool, style: AppStyle) -> some View {
        scaleEffect(pressed && style.pack.motion != .reduced ? 0.98 : 1)
            .opacity(pressed ? 0.9 : 1)
            .animation(style.pack.motion.emphasis.animation, value: pressed)
    }
}

#Preview("Buttons") {
    PackPreviews {
        Button("Save") {}.buttonStyle(PrimaryButtonStyle())
        Button("Save") {}.buttonStyle(PrimaryButtonStyle()).disabled(true)
        Button("Cancel") {}.buttonStyle(SecondaryButtonStyle())
        Button("Try again") {}.buttonStyle(TextButtonStyle())
    }
}
