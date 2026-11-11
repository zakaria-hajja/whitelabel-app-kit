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
            logger: logger
        )
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
