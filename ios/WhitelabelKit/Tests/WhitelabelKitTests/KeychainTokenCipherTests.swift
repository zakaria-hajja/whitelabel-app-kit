import XCTest
import Shared
@testable import WhitelabelKit

final class KeychainTokenCipherTests: XCTestCase {

    private let service = "com.whitelabel.kit.cipher-tests"

    override func tearDown() { KeychainTokenCipher(service: service).deleteKey() }

    func testRoundTrips() throws {
        let cipher = KeychainTokenCipher(service: service)
        let plain = Data("{\"accessToken\":\"a\",\"refreshToken\":\"r\"}".utf8)
        let sealed = try cipher.encrypt(plain: plain.kotlinByteArray)
        XCTAssertEqual(sealed.iv.size, 12)
        XCTAssertNotEqual(Data(kotlin: sealed.ciphertext), plain)
        XCTAssertEqual(Data(kotlin: try cipher.decrypt(sealed: sealed)), plain)
    }

    /** A reinstalled app or a restored backup has no key: decrypting must throw (TokenStore then reads signed out), never crash. */
    func testMissingKeyReadsAsSignedOut() throws {
        let cipher = KeychainTokenCipher(service: service)
        let sealed = try cipher.encrypt(plain: Data("tokens".utf8).kotlinByteArray)
        cipher.deleteKey()
        XCTAssertThrowsError(try cipher.decrypt(sealed: sealed))
    }
}
