package com.whitelabel.core.data.preferences

import com.whitelabel.core.datastore.JsonStore
import com.whitelabel.core.domain.ThemePreference
import com.whitelabel.core.domain.UserPreferencesRepository
import okio.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

@Serializable
private data class PreferencesDocument(val theme: String? = null)

/** Device-wide preferences in their own file, kept on sign-out. */
internal class DefaultUserPreferencesRepository(path: Path, scope: CoroutineScope) : UserPreferencesRepository {
    private val store = JsonStore(path, PreferencesDocument.serializer(), PreferencesDocument(), scope)

    override val theme: Flow<ThemePreference?> =
        store.data.map { document -> ThemePreference.entries.firstOrNull { it.name == document.theme } }

    override suspend fun setTheme(theme: ThemePreference) = store.update { it.copy(theme = theme.name) }
}
