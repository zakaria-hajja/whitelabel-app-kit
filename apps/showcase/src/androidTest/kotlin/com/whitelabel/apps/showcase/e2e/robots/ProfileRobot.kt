package com.whitelabel.apps.showcase.e2e.robots

import androidx.compose.ui.test.junit4.ComposeTestRule
import com.whitelabel.core.testing.android.robots.Robot

class ProfileRobot(compose: ComposeTestRule) : Robot(compose) {

    fun changeFirstName(name: String) {
        type("First name", name)
        tap("Save")
        sees("Profile saved.")
    }

    fun changePassword(current: String, new: String) {
        tap("Change password")
        type("Current password", current)
        type("New password", new)
        type("Confirm new password", new)
        tap("Change password")
    }

    fun showsWrongCurrentPassword() = sees("The current password is wrong.")
}
