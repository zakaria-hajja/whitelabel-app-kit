package com.whitelabel.apps.showcase.e2e.robots

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.ComposeTestRule
import com.whitelabel.core.testing.android.robots.Robot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe

class HomeRobot(compose: ComposeTestRule) : Robot(compose) {

    fun greets(name: String) = sees("Hello, $name")

    fun hasShortcut(label: String): Boolean = texts(label).isNotEmpty()

    /** Pulls the greeting down far enough to trigger a refresh, then waits for it to settle. */
    fun pullToRefresh(name: String) {
        greets(name)
        compose.onNodeWithText("Hello, $name").performTouchInput {
            swipe(start = Offset(centerX, top), end = Offset(centerX, top + 900f), durationMillis = 400)
        }
        compose.waitForIdle()
    }
}
