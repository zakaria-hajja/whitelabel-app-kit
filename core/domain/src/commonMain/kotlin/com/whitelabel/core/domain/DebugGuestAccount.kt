package com.whitelabel.core.domain

/**
 * A ready-made account for Debug builds on the in-memory fake backend: the sign-in screen offers
 * "Continue as guest (debug)" with it. Bound only by `:core:mockapi`, which release builds never
 * link, so release apps can't offer it.
 */
data class DebugGuestAccount(val email: String, val password: String)
