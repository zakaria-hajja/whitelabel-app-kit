import GoogleMobileAds
import Shared
import SwiftUI
import UIKit
import WhitelabelKit

/**
 * Google Mobile Ads native ads ("Native advanced") for the kit's `NativeAdItem`, the twin of
 * Android's `GoogleNativeAdHost`. The SDK starts on the first ad the rules let load, as for banners.
 * Every request follows [policy] (`ads.nonPersonalized`). AdChoices sits in the top-right corner,
 * and video starts muted with the SDK's own controls. An ad without a headline or media is a silent
 * failed load.
 */
struct GoogleNativeAdHost: NativeAdHost {
    let policy: AdRequestPolicy

    @MainActor func load(unitId: String, onLoaded: @escaping (NativeAdHandle) -> Void, onFailed: @escaping () -> Void) {
        Task { @MainActor in
            guard await StartOnce.mobileAds.run({ _ = await MobileAds.shared.start() }) else {
                onFailed()
                return
            }
            NativeAdLoad(unitId: unitId, policy: policy, onLoaded: onLoaded, onFailed: onFailed).start()
        }
    }

    @MainActor func item(ad: NativeAdHandle) -> AnyView {
        guard let google = ad as? GoogleNativeAd, let native = google.ad else { return AnyView(EmptyView()) }
        // A new ad is a new NativeAdView: the template is built once per ad.
        return AnyView(NativeAdTemplateView(ad: native).id(ObjectIdentifier(native)))
    }

    /** The card's height for the app's text size, computed once per draw so the card never changes size on screen. */
    var itemHeight: CGFloat { NativeAdTemplate.height(fontScale: NativeAdTemplate.fontScale()) }
}

/** A loaded Google native ad. iOS's SDK has no destroy: [destroy] drops the reference so it can be freed. */
final class GoogleNativeAd: NSObject, NativeAdHandle {
    private(set) var ad: NativeAd?

    init(ad: NativeAd) { self.ad = ad }

    func destroy() { ad = nil }
}

/** One request: keeps itself (and its loader) alive until the SDK's one callback. */
@MainActor
private final class NativeAdLoad: NSObject, NativeAdLoaderDelegate {
    private let unitId: String
    private let policy: AdRequestPolicy
    private let onLoaded: (NativeAdHandle) -> Void
    private let onFailed: () -> Void
    private var loader: AdLoader?
    private var keepAlive: NativeAdLoad?

    init(unitId: String, policy: AdRequestPolicy, onLoaded: @escaping (NativeAdHandle) -> Void, onFailed: @escaping () -> Void) {
        self.unitId = unitId
        self.policy = policy
        self.onLoaded = onLoaded
        self.onFailed = onFailed
    }

    func start() {
        let adChoices = NativeAdViewAdOptions()
        adChoices.preferredAdChoicesPosition = .topRightCorner
        let video = VideoOptions()
        video.shouldStartMuted = true
        // The SDK presents the click-through from this controller.
        let loader = AdLoader(adUnitID: unitId, rootViewController: Self.rootViewController(), adTypes: [.native], options: [adChoices, video])
        loader.delegate = self
        self.loader = loader
        keepAlive = self
        loader.load(GoogleBannerHost.request(policy: policy))
    }

    func adLoader(_ adLoader: AdLoader, didReceive nativeAd: NativeAd) {
        let media = nativeAd.mediaContent
        if NativeAdTemplate.isRenderable(headline: nativeAd.headline, hasMedia: media.hasVideoContent || media.mainImage != nil) {
            onLoaded(GoogleNativeAd(ad: nativeAd))
        } else {
            onFailed()
        }
        finish()
    }

    func adLoader(_ adLoader: AdLoader, didFailToReceiveAdWithError error: Swift.Error) {
        onFailed()
        finish()
    }

    private func finish() {
        loader = nil
        keepAlive = nil
    }

    private static func rootViewController() -> UIViewController? {
        let windows = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.flatMap(\.windows)
        return (windows.first(where: \.isKeyWindow) ?? windows.first)?.rootViewController
    }
}

/**
 * Sizes of the native list-item template (the Designer's option A, "media left"), the same numbers
 * as Android's `NativeAdTemplate` in dp. [height] is the card at text size ≤ the default; [height(fontScale:)]
 * grows it with Dynamic Type (capped at accessibility2), computed once so the card never jumps.
 */
enum NativeAdTemplate {
    /** The media view, square; the policy minimum is 120. */
    static let mediaSize: CGFloat = 120
    /** The "Ad" chip's minimum size; the policy minimum is 15 px. */
    static let badgeMinSize: CGFloat = 16
    /** The top-right square kept free for the SDK's AdChoices icon. */
    static let adChoicesInset: CGFloat = 24
    /** The card's height at font scale ≤ 1. */
    static let height: CGFloat = height(fontScale: 1)
    /** The "Ad" chip's horizontal padding (spec: 6, between the kit's xs and sm). */
    static let badgePadding: CGFloat = 6
    static let iconSize: CGFloat = 40
    static let ctaMinHeight: CGFloat = 40
    /** The largest text size the card follows (above it, text would not fit a 185 pt column). */
    static let maxTypeSize = DynamicTypeSize.accessibility2

    /**
     * The card's height at [fontScale]: padding 16 + header max(24, 16s) + gap 8 + main row
     * max(media, headline 2×20s + 4 + body 2×16s + 4 + bottom row max(40, 20s + 20)) + padding 16, rounded up.
     */
    static func height(fontScale: CGFloat) -> CGFloat {
        let s = max(fontScale, 0)
        let header = max(24, 16 * s)
        let column = 40 * s + 4 + 32 * s + 4 + max(40, 20 * s + 20)
        let main = max(mediaSize, column)
        // Float(): the same single-precision rounding as Android, so both platforms pin the same heights.
        return CGFloat(Float(16 + header + 8 + main + 16).rounded(.up))
    }

    /** The app's text-size scale (1 at the default size), capped at [maxTypeSize]. */
    static func fontScale() -> CGFloat {
        let cap = UIFontMetrics.default.scaledValue(for: 1, compatibleWith: UITraitCollection(preferredContentSizeCategory: UIContentSizeCategory(maxTypeSize)))
        return min(UIFontMetrics.default.scaledValue(for: 1), cap)
    }

    /** Whether the template can draw an ad: it needs a headline and media (the SDK's required assets). */
    static func isRenderable(headline: String?, hasMedia: Bool) -> Bool {
        !(headline ?? "").isEmpty && hasMedia
    }
}

/** The template in SwiftUI: a [NativeAdView] built in code from the app's style. */
private struct NativeAdTemplateView: UIViewRepresentable {
    let ad: NativeAd
    @SwiftUI.Environment(\.appStyle) private var style
    @SwiftUI.Environment(\.dynamicTypeSize) private var typeSize

    func makeUIView(context: Context) -> NativeAdView {
        NativeAdViewBuilder(style: style, typeSize: min(typeSize, NativeAdTemplate.maxTypeSize)).build(ad)
    }

    func updateUIView(_ view: NativeAdView, context: Context) {}
}

/**
 * Builds the fixed list-item template for one ad: a bordered `surface` card with the "Ad" chip
 * top-left, the advertiser beside it, the media (fit, never cropped) on the left, then the headline,
 * body, icon and an outlined CTA. Themed only by the style's tokens. Only the registered asset views
 * open the ad: the container has no gesture, and the CTA doesn't take touches (the SDK handles the
 * click on the asset view). Text is never rewritten, only truncated. The top-right
 * [NativeAdTemplate.adChoicesInset] square is left to the SDK's AdChoices icon, and the card doesn't
 * clip, so the icon is never cut.
 */
@MainActor
private struct NativeAdViewBuilder {
    let style: AppStyle
    let typeSize: DynamicTypeSize

    func build(_ ad: NativeAd) -> NativeAdView {
        let colors = style.colors
        let corners = style.pack.corners
        let card = NativeAdView()
        card.backgroundColor = UIColor(colors.surface)
        card.layer.cornerRadius = corners.large
        card.layer.borderWidth = 1
        card.clipsToBounds = false

        // Header: the chip, then the advertiser; it ends before the AdChoices corner.
        let chipLabel = label(NativeAds.label, size: 11, style: .caption2, weight: .medium, color: colors.surface, lines: 1)
        let chip = UIView()
        chip.backgroundColor = UIColor(colors.onSurface)
        chip.layer.cornerRadius = corners.small
        chip.addSubview(chipLabel)
        pin(chipLabel, in: chip, horizontal: NativeAdTemplate.badgePadding)
        chip.setContentCompressionResistancePriority(.required, for: .horizontal)
        chipLabel.setContentCompressionResistancePriority(.required, for: .horizontal)
        let header = UIStackView(arrangedSubviews: [chip])
        header.axis = .horizontal
        header.spacing = Spacing.sm
        header.alignment = .center
        if let advertiser = ad.advertiser, !advertiser.isEmpty {
            let view = label(advertiser, size: 12, style: .caption1, weight: .regular, color: colors.onSurfaceVariant, lines: 1)
            header.addArrangedSubview(view)
            card.advertiserView = view
        }

        let media = MediaView()
        media.contentMode = .scaleAspectFit
        media.backgroundColor = UIColor(colors.surface)
        media.mediaContent = ad.mediaContent
        card.mediaView = media

        let headline = label(ad.headline ?? "", size: 14, style: .subheadline, weight: .medium, color: colors.onSurface, lines: 2)
        card.headlineView = headline
        let column = UIStackView(arrangedSubviews: [headline])
        column.axis = .vertical
        column.spacing = Spacing.xs
        column.alignment = .leading
        if let body = ad.body, !body.isEmpty {
            let view = label(body, size: 12, style: .caption1, weight: .regular, color: colors.onSurfaceVariant, lines: 2)
            column.addArrangedSubview(view)
            card.bodyView = view
        }

        let bottom = UIStackView()
        bottom.axis = .horizontal
        bottom.spacing = Spacing.sm
        bottom.alignment = .center
        if let image = ad.icon?.image {
            let icon = UIImageView(image: image)
            icon.contentMode = .scaleAspectFit
            icon.layer.cornerRadius = corners.small
            icon.clipsToBounds = true
            icon.translatesAutoresizingMaskIntoConstraints = false
            NSLayoutConstraint.activate([
                icon.widthAnchor.constraint(equalToConstant: NativeAdTemplate.iconSize),
                icon.heightAnchor.constraint(equalToConstant: NativeAdTemplate.iconSize),
            ])
            bottom.addArrangedSubview(icon)
            card.iconView = icon
        }
        if let action = ad.callToAction, !action.isEmpty {
            let text = label(action, size: 14, style: .subheadline, weight: .medium, color: colors.primary, lines: 1)
            let cta = UIView()
            cta.layer.cornerRadius = corners.small
            cta.layer.borderWidth = 1
            cta.addSubview(text)
            pin(text, in: cta, horizontal: Spacing.md)
            cta.heightAnchor.constraint(greaterThanOrEqualToConstant: NativeAdTemplate.ctaMinHeight).isActive = true
            // The SDK handles the click on the registered view; the CTA itself must not take it.
            cta.isUserInteractionEnabled = false
            bottom.addArrangedSubview(cta)
            card.callToActionView = cta
        }

        for view in [header, media, column, bottom] as [UIView] {
            view.translatesAutoresizingMaskIntoConstraints = false
            card.addSubview(view)
        }
        let padding = Spacing.md
        NSLayoutConstraint.activate([
            header.topAnchor.constraint(equalTo: card.topAnchor, constant: padding),
            header.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: padding),
            header.trailingAnchor.constraint(lessThanOrEqualTo: card.trailingAnchor, constant: -(padding + NativeAdTemplate.adChoicesInset)),
            header.heightAnchor.constraint(greaterThanOrEqualToConstant: 24),
            chip.widthAnchor.constraint(greaterThanOrEqualToConstant: NativeAdTemplate.badgeMinSize),
            chip.heightAnchor.constraint(greaterThanOrEqualToConstant: NativeAdTemplate.badgeMinSize),

            media.topAnchor.constraint(equalTo: header.bottomAnchor, constant: Spacing.sm),
            media.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: padding),
            media.widthAnchor.constraint(equalToConstant: NativeAdTemplate.mediaSize),
            media.heightAnchor.constraint(equalToConstant: NativeAdTemplate.mediaSize),

            column.topAnchor.constraint(equalTo: media.topAnchor),
            column.leadingAnchor.constraint(equalTo: media.trailingAnchor, constant: Spacing.md),
            column.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -padding),

            bottom.leadingAnchor.constraint(equalTo: column.leadingAnchor),
            bottom.trailingAnchor.constraint(lessThanOrEqualTo: card.trailingAnchor, constant: -padding),
            bottom.bottomAnchor.constraint(greaterThanOrEqualTo: media.bottomAnchor),
            bottom.bottomAnchor.constraint(lessThanOrEqualTo: card.bottomAnchor, constant: -padding),
            bottom.topAnchor.constraint(greaterThanOrEqualTo: column.bottomAnchor, constant: Spacing.xs),
        ])
        // The bottom row sits on the media's bottom edge unless larger text pushes it down.
        let onMediaEdge = bottom.bottomAnchor.constraint(equalTo: media.bottomAnchor)
        onMediaEdge.priority = .defaultHigh
        onMediaEdge.isActive = true

        // Layer colors don't follow light/dark by themselves.
        let ctaView = card.callToActionView
        let border = UIColor(colors.outlineVariant)
        let ctaBorder = UIColor(colors.outline)
        let applyBorders = { (view: UIView) in
            view.layer.borderColor = border.resolvedColor(with: view.traitCollection).cgColor
            ctaView?.layer.borderColor = ctaBorder.resolvedColor(with: view.traitCollection).cgColor
        }
        applyBorders(card)
        card.registerForTraitChanges([UITraitUserInterfaceStyle.self]) { (view: NativeAdView, _: UITraitCollection) in applyBorders(view) }

        // Set last, once every asset view is registered.
        card.nativeAd = ad
        return card
    }

    /** A label in the brand's body font at [size], scaled for the (capped) text size, never resized on screen. */
    private func label(_ text: String, size: CGFloat, style textStyle: UIFont.TextStyle, weight: UIFont.Weight, color: Color, lines: Int) -> UILabel {
        let view = UILabel()
        view.text = text
        view.font = font(size: size, style: textStyle, weight: weight)
        view.textColor = UIColor(color)
        view.numberOfLines = lines
        view.lineBreakMode = .byTruncatingTail
        return view
    }

    private func font(size: CGFloat, style textStyle: UIFont.TextStyle, weight: UIFont.Weight) -> UIFont {
        let base: UIFont
        if style.fonts.hasCustomBody, let name = style.fonts.bodyName, let custom = UIFont(name: name, size: size) {
            let descriptor = custom.fontDescriptor.addingAttributes([.traits: [UIFontDescriptor.TraitKey.weight: weight]])
            base = UIFont(descriptor: descriptor, size: size)
        } else {
            base = .systemFont(ofSize: size, weight: weight)
        }
        let traits = UITraitCollection(preferredContentSizeCategory: UIContentSizeCategory(typeSize))
        return UIFontMetrics(forTextStyle: textStyle).scaledFont(for: base, compatibleWith: traits)
    }

    private func pin(_ child: UIView, in parent: UIView, horizontal: CGFloat) {
        child.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            child.leadingAnchor.constraint(equalTo: parent.leadingAnchor, constant: horizontal),
            child.trailingAnchor.constraint(equalTo: parent.trailingAnchor, constant: -horizontal),
            child.centerYAnchor.constraint(equalTo: parent.centerYAnchor),
            child.topAnchor.constraint(greaterThanOrEqualTo: parent.topAnchor),
            child.bottomAnchor.constraint(lessThanOrEqualTo: parent.bottomAnchor),
        ])
    }
}
