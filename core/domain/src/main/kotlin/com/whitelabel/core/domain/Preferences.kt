package com.whitelabel.core.domain

import kotlinx.coroutines.flow.Flow

enum class ThemePreference { SYSTEM, LIGHT, DARK }

/** Device-wide choices, kept across sign-out. */
interface UserPreferencesRepository {
    /** `null` until the user picks one; the app config's default applies meanwhile. */
    val theme: Flow<ThemePreference?>

    suspend fun setTheme(theme: ThemePreference)
}
