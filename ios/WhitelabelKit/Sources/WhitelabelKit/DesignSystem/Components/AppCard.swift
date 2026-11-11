import SwiftUI

/** A container on the background: a hairline outline (Gallery) or a soft shadow (Material). */
public struct AppCard<Content: View>: View {
    @Environment(\.appStyle) private var style
    private let content: Content

    public init(@ViewBuilder content: () -> Content) { self.content = content() }

    public var body: some View {
        let shape = RoundedRectangle(cornerRadius: style.pack.corners.large)
        content
            .padding(Spacing.md)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(style.colors.surface, in: shape)
            .overlay {
                if style.pack.surfaces == .hairline { shape.strokeBorder(style.colors.outline, lineWidth: 1) }
            }
            .shadow(color: style.pack.surfaces == .shadow ? .black.opacity(0.12) : .clear, radius: 6, y: 2)
    }
}

#Preview("Card") {
    ForEach([StylePack.material, .gallery], id: \.name) { pack in
        AppCard { Text("Netflix").font(AppStyle(pack: pack).typography.title) }
            .padding()
            .appStyle(AppStyle(pack: pack))
    }
}
