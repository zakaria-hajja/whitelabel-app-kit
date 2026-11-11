import CryptoKit
import Foundation
import Security
import Shared

/**
 * The session tokens' cipher on iOS: AES-GCM (CryptoKit) with a 256-bit key kept in the Keychain,
 * readable after first unlock, on this device only and never synced. A restored backup or a new
 * device has no key, so the token file can't be opened and reads as signed out (TokenStore clears
 * it), as on Android.
 */
public final class KeychainTokenCipher: NSObject, TokenCipher {
    private let service: String
    private let account = "session-token-key"
    private static let tagLength = 16

    public init(service: String) {
        self.service = service
    }

    public func encrypt(plain: KotlinByteArray) throws -> Sealed {
        let box = try AES.GCM.seal(Data(kotlin: plain), using: try key(createIfMissing: true))
        return Sealed(iv: Data(box.nonce).kotlinByteArray, ciphertext: (box.ciphertext + box.tag).kotlinByteArray)
    }

    public func decrypt(sealed: Sealed) throws -> KotlinByteArray {
        let combined = Data(kotlin: sealed.ciphertext)
        guard combined.count >= Self.tagLength else { throw CipherError.malformed }
        let box = try AES.GCM.SealedBox(
            nonce: AES.GCM.Nonce(data: Data(kotlin: sealed.iv)),
            ciphertext: combined.dropLast(Self.tagLength),
            tag: combined.suffix(Self.tagLength)
        )
        return try AES.GCM.open(box, using: try key(createIfMissing: false)).kotlinByteArray
    }

    /** Removes the key (tests, and a full reset): what was encrypted with it can't be read again. */
    public func deleteKey() {
        SecItemDelete(query() as CFDictionary)
    }

    private func key(createIfMissing: Bool) throws -> SymmetricKey {
        var query = query()
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        if status == errSecSuccess, let data = item as? Data { return SymmetricKey(data: data) }
        guard status == errSecItemNotFound, createIfMissing else { throw CipherError.noKey(status) }
        let key = SymmetricKey(size: .bits256)
        var add = self.query()
        add[kSecValueData as String] = key.withUnsafeBytes { Data($0) }
        add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        let added = SecItemAdd(add as CFDictionary, nil)
        guard added == errSecSuccess else { throw CipherError.noKey(added) }
        return key
    }

    private func query() -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecAttrSynchronizable as String: false,
        ]
    }

    enum CipherError: Swift.Error {
        case noKey(OSStatus)
        case malformed
    }
}
