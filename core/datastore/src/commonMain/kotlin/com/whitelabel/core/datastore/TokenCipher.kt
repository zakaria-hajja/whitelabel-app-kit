package com.whitelabel.core.datastore

/** Ciphertext plus the IV it was encrypted with. */
class Sealed(val iv: ByteArray, val ciphertext: ByteArray)

/** Authenticated encryption for the session tokens. Both operations throw on failure (Android: `GeneralSecurityException`). */
interface TokenCipher {
    fun encrypt(plain: ByteArray): Sealed

    fun decrypt(sealed: Sealed): ByteArray
}
