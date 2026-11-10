package com.whitelabel.core.shell

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class DeepLinkPolicyTest {

    @ParameterizedTest(name = "inMain={0} inAuth={1} signedIn={2} hasGate={3} → {4}")
    @CsvSource(
        // No gate: the app is public, main links open, nothing else exists.
        "true,  false, false, false, OPEN",
        "false, false, false, false, IGNORE",
        // Gate, main link: open when signed in, hold until sign-in otherwise.
        "true,  false, true,  true,  OPEN",
        "true,  false, false, true,  HOLD",
        // Gate, auth link (e.g. reset password): only while signed out.
        "false, true,  false, true,  OPEN",
        "false, true,  true,  true,  IGNORE",
        // Unknown link.
        "false, false, true,  true,  IGNORE",
        "false, false, false, true,  IGNORE",
    )
    internal fun `deep link action`(inMain: Boolean, inAuth: Boolean, signedIn: Boolean, hasGate: Boolean, expected: DeepLinkAction) {
        assertThat(deepLinkAction(inMain = inMain, inAuth = inAuth, signedIn = signedIn, hasGate = hasGate)).isEqualTo(expected)
    }

}
