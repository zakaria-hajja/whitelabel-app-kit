import GoogleMobileAds
import SwiftUI
import WhitelabelKit

/**
 * Google Mobile Ads banners for the kit's `AdBanner`: a large anchored adaptive banner for the
 * available width. The SDK starts on the first banner the rules let load (consent is resolved by
 * then), never at app startup; a failed start is retried by the next banner.
 */
struct GoogleBannerHost: BannerHost {
    func banner(unitId: String, onLoaded: @escaping () -> Void, onFailed: @escaping () -> Void) -> AnyView {
        AnyView(GoogleBanner(unitId: unitId, onLoaded: onLoaded, onFailed: onFailed))
    }
}

private struct GoogleBanner: View {
    let unitId: String
    let onLoaded: () -> Void
    let onFailed: () -> Void

    @State private var started = false
    @State private var width: CGFloat = 0

    var body: some View {
        VStack(spacing: 0) {
            // Measures the width the banner may use, without taking any height.
            Color.clear
                .frame(maxWidth: .infinity, maxHeight: 0)
                .onGeometryChange(for: CGFloat.self, of: { $0.size.width }) { width = $0 }
            if started, width > 0 {
                let size = largeAnchoredAdaptiveBanner(width: width)
                // A new unit or width is a new banner view and a new request.
                GoogleBannerRepresentable(unitId: unitId, size: size, onLoaded: onLoaded, onFailed: onFailed)
                    .frame(width: size.size.width, height: size.size.height)
                    .id("\(unitId)@\(Int(width))")
            }
        }
        .task {
            let ok = await StartOnce.mobileAds.run { _ = await MobileAds.shared.start() }
            if ok { started = true } else { onFailed() }
        }
    }
}

private struct GoogleBannerRepresentable: UIViewRepresentable {
    let unitId: String
    let size: AdSize
    let onLoaded: () -> Void
    let onFailed: () -> Void

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> BannerView {
        let banner = BannerView(adSize: size)
        banner.adUnitID = unitId
        context.coordinator.update(onLoaded: onLoaded, onFailed: onFailed)
        banner.delegate = context.coordinator
        banner.load(Request())
        return banner
    }

    func updateUIView(_ view: BannerView, context: Context) {
        context.coordinator.update(onLoaded: onLoaded, onFailed: onFailed)
    }

    /** Reports the SDK's outcome; a refresh that fails after a load keeps the last ad (the kit's `BannerLoad`). */
    final class Coordinator: NSObject, BannerViewDelegate {
        private var onLoaded: () -> Void = {}
        private var onFailed: () -> Void = {}

        func update(onLoaded: @escaping () -> Void, onFailed: @escaping () -> Void) {
            self.onLoaded = onLoaded
            self.onFailed = onFailed
        }

        func bannerViewDidReceiveAd(_ bannerView: BannerView) { onLoaded() }

        func bannerView(_ bannerView: BannerView, didFailToReceiveAdWithError error: Swift.Error) { onFailed() }
    }
}
