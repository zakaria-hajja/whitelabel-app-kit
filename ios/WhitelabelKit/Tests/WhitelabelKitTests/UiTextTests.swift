import XCTest
import Shared
@testable import WhitelabelKit

final class UiTextTests: XCTestCase {

    /** Shared strings ship with the app (the Gradle build phase syncs them) and resolve in English here. */
    func testResolvesASharedString() async throws {
        let text = DataErrorTextKt.toUiText(DataErrorNetwork.noInternet.toKotlinEnum())
        let resolved = try await Texts.shared.resolve(text: text)
        XCTAssertEqual(resolved, "No internet connection. Check your connection and try again.")
    }

    /**
     * Review Focus 5: a language picked in the app goes to AppleLanguages and applies at the next
     * launch; shared messages resolved before then stay in the launch language (no half-translated screen).
     */
    func testASharedMessageStaysInTheLaunchLanguageAfterAPick() async throws {
        let text = DataErrorTextKt.toUiText(DataErrorNetwork.noInternet.toKotlinEnum())
        let before = try await Texts.shared.resolve(text: text)
        let language = IosLanguageRepository(defaults: .standard)
        language.set(languageTag: "fr")
        defer { language.set(languageTag: nil) }
        let after = try await Texts.shared.resolve(text: text)
        XCTAssertEqual(after, before)
    }
}
