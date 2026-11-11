package com.whitelabel.apps.showcase.e2e.robots

import androidx.compose.ui.test.junit4.ComposeTestRule

class SignInRobot(compose: ComposeTestRule) : Robot(compose) {

    fun isShown() = sees(TITLE)

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
        sees(TITLE)
    }

    private companion object {
        const val TITLE = "Sign in to Showcase"
    }
}
