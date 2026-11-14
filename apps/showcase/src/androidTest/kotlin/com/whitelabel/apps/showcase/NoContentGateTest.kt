package com.whitelabel.apps.showcase

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import com.whitelabel.core.shell.FoundationActivity
import com.whitelabel.core.testing.android.FreshAppRule
import com.whitelabel.core.testing.android.waitForText
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/** Showcase binds no content gate, so FoundationActivity takes today's branch: no gated root. */
class NoContentGateTest {

    private val compose = createAndroidComposeRule<FoundationActivity>()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(FreshAppRule()).around(compose)

    @Test fun noContentGate_takesTodaysBranch() {
        compose.waitForText("Sign in to Showcase")
        // CONTENT_GATE_ROOT_TAG is internal to :core:shell; its ContentGateTest pins this literal.
        compose.onAllNodesWithTag("ShellContentGateRoot", useUnmergedTree = true).assertCountEquals(0)
    }
}
