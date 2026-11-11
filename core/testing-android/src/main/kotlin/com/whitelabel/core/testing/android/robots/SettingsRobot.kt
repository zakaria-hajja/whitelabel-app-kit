package com.whitelabel.core.testing.android.robots

import androidx.compose.ui.test.junit4.ComposeTestRule

/** The shared settings screen. */
class SettingsRobot(compose: ComposeTestRule) : Robot(compose) {

    fun signOut() {
        scrollTo("Sign out")
        tap("Sign out")
        sees("Sign out?")
        tap("Sign out")
    }
}
