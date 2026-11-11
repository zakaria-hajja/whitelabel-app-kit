package com.whitelabel.core.datastore

import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeystoreTokenCipherTest {

    private val cipher = KeystoreTokenCipher(alias = "foundation_test_key")

    @Test fun roundTrip() {
        val plain = "secret-token".encodeToByteArray()
        assertThat(cipher.decrypt(cipher.encrypt(plain)).decodeToString()).isEqualTo("secret-token")
    }

    @Test fun twoEncryptions_useDifferentIvs() {
        val plain = "same".encodeToByteArray()
        val first = cipher.encrypt(plain)
        val second = cipher.encrypt(plain)
        assertThat(first.iv.contentEquals(second.iv)).isFalse()
        assertThat(first.ciphertext.contentEquals(second.ciphertext)).isFalse()
    }
}
