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
}
