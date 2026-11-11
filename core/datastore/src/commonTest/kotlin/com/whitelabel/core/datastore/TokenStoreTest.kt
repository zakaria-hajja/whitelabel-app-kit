package com.whitelabel.core.datastore

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import assertk.assertions.isNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

/** Reversible stand-in for the Keystore cipher; [failDecrypt] simulates an invalidated key. */
private class XorCipher(var failDecrypt: Boolean = false, var keystoreFault: Boolean = false) : TokenCipher {
    private val iv = byteArrayOf(7, 7, 7)
    override fun encrypt(plain: ByteArray) = Sealed(iv, plain.map { (it.toInt() xor 0x5A).toByte() }.toByteArray())
    override fun decrypt(sealed: Sealed): ByteArray {
        if (failDecrypt) throw IllegalStateException("key invalidated")
        if (keystoreFault) throw RuntimeException("keystore unavailable")
        return sealed.ciphertext.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
    }
}

class TokenStoreTest {

    private val fileSystem = FakeFileSystem()

    private val file = "/data/session.json".toPath().also { fileSystem.createDirectories(it.parent!!) }
    private val tokens = StoredTokens("access-123", "refresh-456")

    private suspend fun <R> withStore(cipher: TokenCipher = XorCipher(), block: suspend (TokenStore) -> R): R {
        val job = Job()
        val store = TokenStore(file, cipher, CoroutineScope(Dispatchers.Default + job), fileSystem)
        return try {
            block(store)
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test fun `written tokens read back`() = runTest {
        withStore { it.write(tokens) }
        assertThat(withStore { it.tokens.first() }).isEqualTo(tokens)
    }

    @Test fun `nothing written reads as signed out`() = runTest {
        assertThat(withStore { it.tokens.first() }).isNull()
    }

    @Test fun `file never contains the plain tokens`() = runTest {
        withStore { it.write(tokens) }
        val content = fileSystem.read(file) { readUtf8() }
        assertThat(content).doesNotContain("access-123")
        assertThat(content).doesNotContain("refresh-456")
    }

    @Test fun `undecryptable data reads as signed out and is cleared`() = runTest {
        withStore { it.write(tokens) }
        assertThat(withStore(XorCipher(failDecrypt = true)) { it.tokens.first() }).isNull()
        // Cleared: even a working key finds nothing afterwards.
        assertThat(withStore { it.tokens.first() }).isNull()
    }

    @Test fun `clear reads as null`() = runTest {
        withStore { store ->
            store.write(tokens)
            store.clear()
        }
        assertThat(withStore { it.tokens.first() }).isNull()
    }

    @Test fun `a keystore runtime fault reads as signed out instead of crashing`() = runTest {
        withStore { it.write(tokens) }
        assertThat(withStore(XorCipher(keystoreFault = true)) { it.tokens.first() }).isNull()
    }

    @Test fun `compare and set writes only over the expected pair`() = runTest {
        val newer = StoredTokens("access-2", "refresh-2")
        withStore { store ->
            store.write(tokens)
            assertThat(store.compareAndSet(expected = newer, new = null)).isFalse()
            assertThat(store.tokens.first()).isEqualTo(tokens)
            assertThat(store.compareAndSet(expected = tokens, new = newer)).isTrue()
            assertThat(store.tokens.first()).isEqualTo(newer)
            store.clear()
            assertThat(store.compareAndSet(expected = newer, new = tokens)).isFalse()
            assertThat(store.tokens.first()).isNull()
        }
    }

    /** The stored document must read back after the move off java.util.Base64: same alphabet, same padding. */
    @Test fun `base64 matches what java util Base64 wrote`() = runTest {
        val fixedIv = object : TokenCipher {
            override fun encrypt(plain: ByteArray) = Sealed(byteArrayOf(0, 1, 2, 3), plain)
            override fun decrypt(sealed: Sealed) = sealed.ciphertext
        }
        withStore(fixedIv) { it.write(StoredTokens("a", "r")) }
        assertThat(fileSystem.read(file) { readUtf8() }).contains("\"iv\":\"AAECAw==\"")
    }
}
