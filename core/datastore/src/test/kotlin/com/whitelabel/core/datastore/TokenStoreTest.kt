package com.whitelabel.core.datastore

import assertk.assertThat
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import java.io.File
import java.security.GeneralSecurityException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Reversible stand-in for the Keystore cipher; [failDecrypt] simulates an invalidated key. */
private class XorCipher(var failDecrypt: Boolean = false) : TokenCipher {
    private val iv = byteArrayOf(7, 7, 7)
    override fun encrypt(plain: ByteArray) = Sealed(iv, plain.map { (it.toInt() xor 0x5A).toByte() }.toByteArray())
    override fun decrypt(sealed: Sealed): ByteArray {
        if (failDecrypt) throw GeneralSecurityException("key invalidated")
        return sealed.ciphertext.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
    }
}

class TokenStoreTest {

    @TempDir
    lateinit var dir: File

    private val file get() = File(dir, "session.json")
    private val tokens = StoredTokens("access-123", "refresh-456")

    private suspend fun <R> withStore(cipher: TokenCipher = XorCipher(), block: suspend (TokenStore) -> R): R {
        val job = Job()
        val store = TokenStore(file, cipher, CoroutineScope(Dispatchers.IO + job))
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
        val content = file.readText()
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
}
