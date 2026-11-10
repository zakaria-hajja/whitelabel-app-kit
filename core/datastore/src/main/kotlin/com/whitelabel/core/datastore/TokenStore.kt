package com.whitelabel.core.datastore

import java.io.File
import java.security.GeneralSecurityException
import java.util.Base64
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
        val sealed = cipher.encrypt(Json.encodeToString(StoredTokens.serializer(), tokens).encodeToByteArray())
        store.update { SealedDocument(iv = base64.encodeToString(sealed.iv), ciphertext = base64.encodeToString(sealed.ciphertext)) }
    }

    suspend fun clear() {
        store.update { SealedDocument() }
    }

    private suspend fun SealedDocument.open(): StoredTokens? {
        if (iv == null || ciphertext == null) return null
        return try {
            val plain = cipher.decrypt(Sealed(base64Decoder.decode(iv), base64Decoder.decode(ciphertext)))
            Json.decodeFromString(StoredTokens.serializer(), plain.decodeToString())
        } catch (_: GeneralSecurityException) {
            null.also { clear() }
        } catch (_: IllegalArgumentException) {
            // Bad Base64 or JSON (SerializationException is an IllegalArgumentException).
            null.also { clear() }
        }
    }

    private companion object {
        val base64: Base64.Encoder = Base64.getEncoder()
        val base64Decoder: Base64.Decoder = Base64.getDecoder()
    }
}
