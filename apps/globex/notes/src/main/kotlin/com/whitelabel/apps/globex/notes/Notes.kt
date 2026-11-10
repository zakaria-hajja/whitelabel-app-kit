package com.whitelabel.apps.globex.notes

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.navigation.compose.composable
import com.whitelabel.core.shell.AppDefinitionBuilder
import com.whitelabel.core.shell.FeatureEntry
import com.whitelabel.core.shell.Tab
import kotlinx.serialization.Serializable

/** Root route of the notes tab; public so the app definition can make it the start tab. */
@Serializable
data object NotesRoute

/** Adds Globex's local-only notes tab. */
fun AppDefinitionBuilder.notes() {
    add(
        FeatureEntry(
            id = "notes",
            tab = Tab(NotesRoute, R.string.notes_tab, Icons.Default.Edit),
            routes = { composable<NotesRoute> { NotesRoot() } },
        ),
    )
}
