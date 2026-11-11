import SwiftUI

/** An item's image: a placeholder while it loads, when there is none, and when it fails. */
struct CatalogImage: View {
    let url: String?
    @SwiftUI.Environment(\.appStyle) private var style

    var body: some View {
        Group {
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
