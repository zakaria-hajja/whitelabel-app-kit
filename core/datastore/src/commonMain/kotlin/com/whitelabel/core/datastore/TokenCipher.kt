package com.whitelabel.core.datastore

/** Ciphertext plus the IV it was encrypted with. */
class Sealed(val iv: ByteArray, val ciphertext: ByteArray)

/** Authenticated encryption for the session tokens. Both operations throw on failure (Android: `GeneralSecurityException`). */
interface TokenCipher {
    // @Throws lets a Swift implementation (iOS's KeychainTokenCipher) throw; TokenStore turns a failure into "signed out".
    @Throws(Exception::class)
    fun encrypt(plain: ByteArray): Sealed

    @Throws(Exception::class)
    fun decrypt(sealed: Sealed): ByteArray
}
