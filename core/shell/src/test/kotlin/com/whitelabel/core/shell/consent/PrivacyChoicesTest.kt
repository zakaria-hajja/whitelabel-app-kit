package com.whitelabel.core.shell.consent

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.whitelabel.core.consent.Consent
import com.whitelabel.core.consent.FakeConsentPort
import org.junit.jupiter.api.Test

class PrivacyChoicesTest {

    private fun consentWith(port: FakeConsentPort): Consent =
        Consent(port).also { it.gatherOnLaunch() }

    @Test fun `row is hidden when privacy options are not required`() {
        val port = FakeConsentPort(next = null, optionsRequired = false)
        val choices = PrivacyChoices(consentWith(port))

        assertThat(choices.visible.value).isFalse()
    }

    @Test fun `row shows and opens the options when required`() {
        val port = FakeConsentPort(next = null, optionsRequired = true)
        val choices = PrivacyChoices(consentWith(port))

        assertThat(choices.visible.value).isTrue()
        choices.open()
        assertThat(port.optionsShown).isEqualTo(1)
    }

    @Test fun `row is hidden and does nothing without a consent`() {
        val choices = PrivacyChoices(null)

        assertThat(choices.visible.value).isFalse()
        choices.open()
    }

    @Test fun `row hides again once the options stop being required`() {
        val port = FakeConsentPort(next = null, optionsRequired = true)
        val choices = PrivacyChoices(consentWith(port))
        port.optionsRequired = false

        choices.open()

        assertThat(choices.visible.value).isFalse()
    }
}
