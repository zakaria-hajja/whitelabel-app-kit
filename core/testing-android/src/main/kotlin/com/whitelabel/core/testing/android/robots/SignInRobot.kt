package com.whitelabel.core.testing.android.robots

import androidx.compose.ui.test.junit4.ComposeTestRule

/** The shared sign-in and registration screens; [appName] as in the app's title. */
class SignInRobot(compose: ComposeTestRule, appName: String) : Robot(compose) {

    private val title = "Sign in to $appName"


    fun isShown() = sees(title)

    fun signIn(email: String, password: String) {
        isShown()
        type("Email", email)
        type("Password", password)
        tap("Sign in")
    }

    fun register(email: String, password: String, firstName: String) {
        isShown()
        tap("Create an account")
        type("Email", email)
        type("Password", password)
        type("First name (optional)", firstName)
        tap("Create account")
    }

    fun showsWrongCredentials() {
        sees("Wrong email or password.")
        sees(title)
    }
}
