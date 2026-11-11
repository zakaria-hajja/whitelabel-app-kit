import SwiftUI

/** The styles previews and the catalog show: Gallery with its sample assets, and Material. */
enum PreviewStyles {
    static var gallery: AppStyle {
        AppStyle(pack: .gallery, colors: BrandColors(prefix: "GalleryPreview"), fonts: BrandFonts(displayName: "InstrumentSerif-Regular", bodyName: "Geist-Regular"))
    }

    static var material: AppStyle { AppStyle(pack: .material) }
}

/** Renders [content] in both packs, light and dark, for #Preview. */
struct PackPreviews<Content: View>: View {
    @ViewBuilder let content: () -> Content

    var body: some View {
        ScrollView {
            VStack(spacing: Spacing.lg) {
                ForEach([PreviewStyles.gallery, PreviewStyles.material], id: \.pack.name) { style in
                    ForEach([ColorScheme.light, .dark], id: \.self) { scheme in
                        VStack(alignment: .leading, spacing: Spacing.sm) {
                            Text("\(style.pack.name), \(scheme == .dark ? "dark" : "light")").font(.caption).foregroundStyle(.secondary)
                            content()
                        }
                        .padding()
                        .background(style.colors.background)
                        .appStyle(style)
                        .environment(\.colorScheme, scheme)
                    }
                }
            }
        }
    }
}
