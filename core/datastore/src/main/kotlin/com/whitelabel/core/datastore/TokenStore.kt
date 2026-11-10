package com.whitelabel.core.datastore

import java.io.File
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The session's token pair as stored. */
@Serializable
data class StoredTokens(val accessToken: String, val refreshToken: String)

@Serializable
private data class SealedDocument(val iv: String? = null, val ciphertext: String? = null)

/**
 * The session tokens, encrypted with [cipher] and kept in [file]. Data that can't be decrypted (the
 * key was invalidated, or the file came from another device) reads as signed out and is cleared.
 */
class TokenStore(file: File, private val cipher: TokenCipher, scope: CoroutineScope) {

    private val store = JsonStore(file, SealedDocument.serializer(), SealedDocument(), scope)

    val tokens: Flow<StoredTokens?> = store.data.map { document -> document.open() }

    suspend fun write(tokens: StoredTokens) {
        val sealed = seal(tokens)
        store.update { sealed }
    }

    suspend fun clear() {
        store.update { SealedDocument() }
    }

    /**
     * Atomically replaces the stored pair with [new] (`null` clears it), only if it currently is
     * [expected]. Returns whether it did: `false` means the session changed meanwhile.
     */
    suspend fun compareAndSet(expected: StoredTokens, new: StoredTokens?): Boolean {
        val replacement = new?.let(::seal) ?: SealedDocument()
        var swapped = false
        store.update { document ->
            swapped = document.decryptOrNull() == expected
            if (swapped) replacement else document
        }
        return swapped
    }

    private fun seal(tokens: StoredTokens): SealedDocument {
        val sealed = cipher.encrypt(Json.encodeToString(StoredTokens.serializer(), tokens).encodeToByteArray())
        return SealedDocument(iv = base64.encodeToString(sealed.iv), ciphertext = base64.encodeToString(sealed.ciphertext))
    }

    private fun SealedDocument.decryptOrNull(): StoredTokens? = try {
        decrypt()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private fun SealedDocument.decrypt(): StoredTokens? {
        if (iv == null || ciphertext == null) return null
        val plain = cipher.decrypt(Sealed(base64Decoder.decode(iv), base64Decoder.decode(ciphertext)))
        return Json.decodeFromString(StoredTokens.serializer(), plain.decodeToString())
    }

    /**
     * Any failure to open the pair reads as signed out and clears it: an invalidated key
     * (`GeneralSecurityException`), bad Base64 or JSON, or a Keystore fault some devices raise as a
     * `ProviderException`. Signing out is recoverable; crashing on every launch is not.
     */
    private suspend fun SealedDocument.open(): StoredTokens? = decryptOrNull() ?: null.also {
        if (iv != null || ciphertext != null) clear()
    }

    private companion object {
        val base64: Base64.Encoder = Base64.getEncoder()
        val base64Decoder: Base64.Decoder = Base64.getDecoder()
    }
}
