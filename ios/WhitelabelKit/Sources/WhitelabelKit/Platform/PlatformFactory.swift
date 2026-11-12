import Foundation
import Shared

/**
 * The iOS platform pieces for the shared graph, with the app's identity read from its Info.plist
 * (`WL*` keys, filled from the generated xcconfig).
 */
public enum PlatformFactory {
    public static func make(bundle: Bundle = .main, logger: @escaping (String) -> Void = { print($0) }) -> IosPlatform {
        IosPlatform(
            tokenCipher: KeychainTokenCipher(service: bundle.bundleIdentifier ?? "whitelabel"),
            language: IosLanguageRepository(),
            brand: brand(from: bundle),
            logger: logger,
            firebase: firebase(from: bundle),
            ads: string(bundle, "WLAds") == "on" ? "on" : "off",
            adsUnits: string(bundle, "WLAdsUnits") ?? "",
            adsDeclared: string(bundle, "WLAdsDeclared") == "true",
            debugBuild: debugBuild
        )
    }

    #if DEBUG
    static let debugBuild = true
    #else
    static let debugBuild = false
    #endif

    /**
     * The app's ads settings: `WLAds` (`$(WL_ADS)`: `on`, else off), `WLAdsUnits` (`$(WL_ADS_UNITS)`)
     * and `WLAdsDeclared` (`$(WL_ADS_DECLARED)`). An app without the keys has no ads.
     */
    static func string(_ bundle: Bundle, _ key: String) -> String? {
        bundle.object(forInfoDictionaryKey: key) as? String
    }

    /** `WLFirebase` (`on`, `fake` or `off`); anything else is off. */
    static func firebase(from bundle: Bundle) -> String {
        let value = bundle.object(forInfoDictionaryKey: "WLFirebase") as? String
        return value == "on" || value == "fake" ? value! : "off"
    }

    static func brand(from bundle: Bundle) -> BrandConfig {
        func value(_ key: String) -> String? {
            (bundle.object(forInfoDictionaryKey: key) as? String).flatMap { $0.isEmpty ? nil : $0 }
        }
        return BrandConfig(
            tenant: value("WLTenant"),
            appName: value("CFBundleDisplayName") ?? value("CFBundleName") ?? "",
            applicationId: bundle.bundleIdentifier ?? "",
            versionName: value("CFBundleShortVersionString") ?? "",
            environment: value("WLEnvironment") == "PRODUCTION" ? .production : .development,
            apiBaseUrl: value("WLApiBaseUrl"),
            deepLinkScheme: value("WLDeepLinkScheme") ?? "",
            runtimeDefaults: RuntimeDefaults(
                supportEmail: value("WLSupportEmail"),
                privacyPolicyUrl: value("WLPrivacyPolicyUrl") ?? "",
                termsUrl: value("WLTermsUrl") ?? "",
                enabledFeatures: Set((value("WLDefaultFeatures") ?? "").split(separator: ",").map(String.init))
            )
        )
    }
}
