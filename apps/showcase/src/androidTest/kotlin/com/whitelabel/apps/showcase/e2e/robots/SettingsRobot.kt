package com.whitelabel.apps.showcase.e2e.robots

import androidx.compose.ui.test.junit4.ComposeTestRule

class SettingsRobot(compose: ComposeTestRule) : Robot(compose) {

    fun signOut() {
        scrollTo("Sign out")
        tap("Sign out")
        sees("Sign out?")
        tap("Sign out")
    }
}
