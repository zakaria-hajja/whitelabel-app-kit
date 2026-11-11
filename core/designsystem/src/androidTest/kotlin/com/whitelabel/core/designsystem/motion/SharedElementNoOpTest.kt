package com.whitelabel.core.designsystem.motion

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.whitelabel.core.designsystem.theme.AppTheme
import org.junit.Rule
import org.junit.Test

/** Outside the shell (previews, tests, screens hosted elsewhere) the shared-element helpers do nothing. */
class SharedElementNoOpTest {

    @get:Rule val compose = createComposeRule()

    @Test fun sharedElementOutsideTheShellRendersNormally() {
        compose.setContent {
            AppTheme {
                Box(Modifier.size(40.dp).sharedElementTransition("k").testTag("a"))
                Box(Modifier.size(40.dp).sharedBoundsTransition("k2").testTag("b"))
            }
        }
        compose.onNodeWithTag("a").assertIsDisplayed()
        compose.onNodeWithTag("b").assertIsDisplayed()
    }
}
