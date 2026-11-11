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
            .background(style.colors.primary.opacity(isEnabled ? 1 : 0.4), in: RoundedRectangle(cornerRadius: style.pack.corners.medium))
            .pressEffect(configuration.isPressed, style: style)
            .sensoryFeedback(.selection, trigger: configuration.isPressed) { _, pressed in pressed && style.pack.haptics[.confirm] != nil }
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
            .overlay(RoundedRectangle(cornerRadius: style.pack.corners.medium).strokeBorder(style.colors.outline))
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

extension View {
    /** The pack's emphasis motion on press (Gallery's soft spring; Reduce Motion: a fade). */
    func pressEffect(_ pressed: Bool, style: AppStyle) -> some View {
        scaleEffect(pressed ? 0.98 : 1)
            .opacity(pressed ? 0.9 : 1)
            .animation(style.pack.motion.emphasis.animation, value: pressed)
    }
}

#Preview("Buttons") {
    ForEach([StylePack.material, .gallery], id: \.name) { pack in
        VStack(spacing: Spacing.sm) {
            Button("Save") {}.buttonStyle(PrimaryButtonStyle())
            Button("Save") {}.buttonStyle(PrimaryButtonStyle()).disabled(true)
            Button("Cancel") {}.buttonStyle(SecondaryButtonStyle())
            Button("Try again") {}.buttonStyle(TextButtonStyle())
        }
        .padding()
        .appStyle(AppStyle(pack: pack))
    }
}
