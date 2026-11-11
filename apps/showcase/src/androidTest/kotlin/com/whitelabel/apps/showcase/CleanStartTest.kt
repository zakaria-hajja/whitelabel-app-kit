package com.whitelabel.apps.showcase

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.whitelabel.core.mockapi.FakeBackend
import com.whitelabel.core.mockapi.FakeData
import com.whitelabel.core.shell.FoundationActivity
import com.whitelabel.core.testing.android.EndAppScopeRule
import com.whitelabel.core.testing.android.ResetAppDataRule
import com.whitelabel.core.testing.android.waitForText
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import org.junit.Assert.assertFalse
import org.junit.Before
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
@HiltAndroidTest
class CleanStartTest {

    private val hilt = HiltAndroidRule(this)
    private val compose = createAndroidComposeRule<FoundationActivity>()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(ResetAppDataRule()).around(hilt).around(EndAppScopeRule()).around(compose)

    @Inject lateinit var backend: FakeBackend

    @Before fun inject() = hilt.inject()

    @Test fun firstTest_signsIn_andLeavesASession() {
        signIn()
        compose.waitForText("Hello, Alice")
    }

    @Test fun secondTest_stillStartsSignedOut() {
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
}
