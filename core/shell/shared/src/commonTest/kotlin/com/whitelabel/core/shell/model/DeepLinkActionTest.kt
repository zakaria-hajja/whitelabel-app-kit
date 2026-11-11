package com.whitelabel.core.shell.model

import kotlin.test.Test
import kotlin.test.assertEquals

class DeepLinkActionTest {

    @Test fun `every case of the deep link policy`() {
        // inMain, inAuth, signedIn, hasGate → expected
        val cases = listOf(
            listOf(true, false, false, false) to DeepLinkAction.OPEN, // no gate: main links open
            listOf(false, false, false, false) to DeepLinkAction.IGNORE,
            listOf(true, false, true, true) to DeepLinkAction.OPEN, // gate: main link signed in
            listOf(true, false, false, true) to DeepLinkAction.HOLD, // gate: held until sign-in
            listOf(false, true, false, true) to DeepLinkAction.OPEN, // auth link (reset) signed out
            listOf(false, true, true, true) to DeepLinkAction.IGNORE, // auth link while signed in
            listOf(false, false, true, true) to DeepLinkAction.IGNORE, // unknown link
            listOf(false, false, false, true) to DeepLinkAction.IGNORE,
        )
        cases.forEach { (input, expected) ->
            val (inMain, inAuth, signedIn, hasGate) = input
            assertEquals(expected, deepLinkAction(inMain, inAuth, signedIn, hasGate), "case $input")
        }
    }
}
