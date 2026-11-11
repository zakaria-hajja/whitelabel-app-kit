import XCTest
import Shared
@testable import WhitelabelKit

/**
 * A reinstalled app or a restored backup has the token file but not the Keychain key: the app must
 * start signed out, never crash (plan Review Focus 3). Runs the real path: Swift cipher → Kotlin
 * TokenStore → session.
 */
@MainActor
final class RestoredSessionTests: XCTestCase {

    override func setUp() async throws { try TestGraph.start() }
    override func tearDown() async throws {
        TestGraph.stop()
        KeychainTokenCipher(service: TestGraph.cipherService).deleteKey()
    }

    func testMissingKeyStartsSignedOut() async throws {
        let shell = SharedGraph.shared.shellViewModel()
        let login = SharedGraph.shared.loginViewModel(email: nil)
        login.onAction(action: LoginActionOnEmailChange(email: "alice@shared.test"))
        login.onAction(action: LoginActionOnPasswordChange(password: "Password123!"))
        login.onAction(action: LoginActionOnSubmit.shared)
        try await waitUntil { shell.state.value.session is SessionStateSignedIn }

        KeychainTokenCipher(service: TestGraph.cipherService).deleteKey()
        try SharedGraph.shared.resetForTests()

        let restarted = SharedGraph.shared.shellViewModel()
        try await waitUntil { restarted.state.value.session is SessionStateSignedOut }
    }
}
