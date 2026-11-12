import SwiftUI

/**
 * An item's image, filling the size its caller gives and clipped to it: a placeholder while it
 * loads, when there is none, and when it fails.
 */
struct CatalogImage: View {
    let url: String?
    @SwiftUI.Environment(\.appStyle) private var style

    var body: some View {
        // Color.clear takes the proposed size; the image fills it in an overlay and is clipped to it,
        // so an image of any aspect ratio never spills out of its box.
        Color.clear
            .overlay {
                if let url, let parsed = URL(string: url) {
                    AsyncImage(url: parsed) { phase in
                        switch phase {
                        case let .success(image): image.resizable().scaledToFill()
                        case .failure: placeholder(systemImage: "photo.badge.exclamationmark")
                        default: placeholder(systemImage: "photo").overlay(ProgressView())
                        }
                    }
                } else {
                    placeholder(systemImage: "photo")
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: style.pack.corners.small))
            .accessibilityHidden(true)
    }

    private func placeholder(systemImage: String) -> some View {
        ZStack {
            style.colors.surfaceVariant
            Image(systemName: systemImage).foregroundStyle(style.colors.onSurfaceVariant)
        }
    }
}

#Preview("Catalog image") {
    PackPreviews {
        HStack {
            CatalogImage(url: nil).frame(width: 48, height: 48)
            CatalogImage(url: "https://invalid.example/missing.png").frame(width: 48, height: 48)
            CatalogImage(url: "https://picsum.photos/400/200").frame(width: 48, height: 48)
        }
        CatalogImage(url: "https://picsum.photos/300/600").frame(maxWidth: .infinity).aspectRatio(16 / 9, contentMode: .fit)
    }
}
