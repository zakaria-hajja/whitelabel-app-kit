import Foundation
import Shared
import WhitelabelConsent
import WhitelabelKit

/**
 * Google Mobile Ads banners and the consent form for an iOS app, the twin of Android's
 * `:core:ads:google`: list it in `AppDefinition(services: [AdsService()], ads: [...])`. It follows
 * `WL_ADS` (`WLAds` in Info.plist):
 * - `on`: hands the shared graph the UMP consent port and the tracking prompt (ATT when
 *   `ads.ios.requestTracking` is on, `WLAdsTracking` yes; otherwise none) and registers Google's
 *   banner host, whose requests carry `npa=1` with `ads.nonPersonalized` (`WLAdsNonPersonalized` yes).
 *   The SDK itself starts lazily, on the first banner the rules let load. The app's
 *   Info.plist must carry the AdMob app ID (`GADApplicationIdentifier`), and with tracking on the
 *   prompt's text (the WhitelabelAdsApp template adds it), or the app stops at launch.
 * - `off` (also Release with `ads.enabled=debug`): nothing; every banner stays hidden.
 */
public struct AdsService: AppService {
    public init() {}

    /** Follows `WL_ADS`. */
    public var kind: AppServiceKind { .ads }

    @MainActor public func install(mode: String) {
        install(mode: mode, info: Bundle.main.infoDictionary ?? [:])
    }

    /** [install] with the Info.plist values given (tests). */
    @MainActor func install(mode: String, info: [String: Any]) {
        guard mode == "on" else { return }
        if let problem = Self.problem(info) { fatalError(problem) }
        SharedGraph.shared.useConsent(port: UmpConsentPort())
        SharedGraph.shared.useAds(tracking: Self.trackingPort(info))
        AdBanners.register(GoogleBannerHost(policy: Self.requestPolicy(info)))
    }

    /** `WLAdsNonPersonalized` (`$(WL_ADS_NON_PERSONALIZED)`, from `ads.nonPersonalized`) is `yes` only with ads on and the setting true. */
    static func requestPolicy(_ info: [String: Any]) -> AdRequestPolicy {
        AdRequestPolicy(nonPersonalized: (info["WLAdsNonPersonalized"] as? String) == "yes")
    }

    /** `WLAdsTracking` (`$(WL_ADS_TRACKING)`) is `yes` only for a build with ads and tracking on. */
    static func tracksUsers(_ info: [String: Any]) -> Bool { (info["WLAdsTracking"] as? String) == "yes" }

    /** ATT with tracking on; otherwise nothing to ask. */
    static func trackingPort(_ info: [String: Any]) -> TrackingPort {
        tracksUsers(info) ? AttTrackingPort() : NoTracking.shared
    }

    /**
     * Why ads can't run, or nil. The SDK stops the app without the AdMob app ID, and iOS stops an app
     * that asks for tracking without the prompt's text.
     */
    static func problem(_ info: [String: Any]) -> String? {
        let id = (info["GADApplicationIdentifier"] as? String)?.trimmingCharacters(in: .whitespaces) ?? ""
        if id.isEmpty {
            return "Ads are on, but Info.plist has no GADApplicationIdentifier (the AdMob app ID); set GADApplicationIdentifier = $(WL_ADMOB_APP_ID) in Info.plist"
        }
        let message = (info["NSUserTrackingUsageDescription"] as? String) ?? ""
        if tracksUsers(info), message.isEmpty {
            return "ads.ios.requestTracking is on, but Info.plist has no NSUserTrackingUsageDescription; use the WhitelabelAdsApp template, which adds it from ads.ios.trackingMessage"
        }
        return nil
    }
}
