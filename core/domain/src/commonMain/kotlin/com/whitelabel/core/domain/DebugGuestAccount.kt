package com.whitelabel.core.domain

/**
 * The ready-made account behind the sign-in screen's "Continue as guest (debug)". Debug builds bind
 * it (Android: the shell's debug source set; iOS: Swift's `DebugControls`), the fake backend
 * replaces it with its own seeded account, and Release never binds it, so release apps can't offer it.
 */
data class DebugGuestAccount(val email: String, val password: String)
