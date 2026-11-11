package com.whitelabel.buildlogic

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test

class IosTestsTest {

    @Test fun `ios tests run on a Mac with the iOS SDK`() {
        assertThat(iosTestsEnabled(skipProperty = null, isMac = true, hasIosSdk = true)).isTrue()
    }

    @Test fun `a Mac without the iOS SDK skips them, in any repo`() {
        assertThat(iosTestsEnabled(skipProperty = null, isMac = true, hasIosSdk = false)).isFalse()
    }

    @Test fun `skipIosTests true turns them off`() {
        assertThat(iosTestsEnabled(skipProperty = "true", isMac = true, hasIosSdk = true)).isFalse()
    }

    @Test fun `any other skip value keeps them on`() {
        listOf("yes", "TRUE ", "false", "").forEach { value ->
            assertThat(iosTestsEnabled(skipProperty = value, isMac = true, hasIosSdk = true)).isTrue()
        }
    }

    @Test fun `off a Mac Kotlin disables the iOS targets itself, so nothing is changed`() {
        assertThat(iosTestsEnabled(skipProperty = null, isMac = false, hasIosSdk = false)).isTrue()
    }
}
