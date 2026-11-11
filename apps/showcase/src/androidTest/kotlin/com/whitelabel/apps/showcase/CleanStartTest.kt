package com.whitelabel.apps.showcase

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.whitelabel.core.mockapi.FakeBackend
import com.whitelabel.core.mockapi.FakeData
import com.whitelabel.core.shell.FoundationActivity
import com.whitelabel.core.testing.android.FreshAppRule
import com.whitelabel.core.testing.android.waitForText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.koin.core.context.GlobalContext
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runners.MethodSorters

/**
 * Two tests in one class: the second must start signed out with a fresh backend, whatever the
 * first left behind (stored tokens, cached user, the fake's sessions).
 */
// The second test proves nothing unless it runs after the first.
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class CleanStartTest {

    private val compose = createAndroidComposeRule<FoundationActivity>()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(FreshAppRule()).around(compose)

    /** This test's fake: read from the graph each time, since every test starts a new one. */
    private val backend: FakeBackend get() = GlobalContext.get().get()

    @Test fun firstTest_signsIn_andLeavesASession() {
        firstBackend = System.identityHashCode(backend)
        signIn()
        compose.waitForText("Hello, Alice")
    }

    @Test fun secondTest_stillStartsSignedOut() {
        assertNotEquals("the second test got the first test's FakeBackend", firstBackend, System.identityHashCode(backend))
        compose.waitForText("Sign in to Showcase")
        assertFalse(backend.requests.toString(), backend.requests.any { it == "GET /api/v1/users/me" })
        signIn()
        compose.waitForText("Hello, Alice")
    }

    private fun signIn() {
        compose.waitForText("Sign in to Showcase")
        compose.onNodeWithText("Email").performTextInput("alice@shared.test")
        compose.onNodeWithText("Password").performTextInput(FakeData.PASSWORD)
        compose.onNodeWithText("Sign in").performClick()
    }

    private companion object {
        /** The first test's FakeBackend, to prove the second gets a new one. */
        var firstBackend = 0
    }
}
