package com.whitelabel.apps.showcase

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.collectAsState
import com.whitelabel.core.domain.remote.RemoteValues
import com.whitelabel.feature.settings.SettingsValueRow
import kotlinx.coroutines.flow.map
import org.koin.compose.getKoin

internal const val REMOTE_NOTE_KEY = "showcase_remote_note"

/** The note Remote Config carries for Showcase; "default" until a value is fetched (or without Firebase). */
internal fun remoteNote(values: RemoteValues): String = values.string(REMOTE_NOTE_KEY, "default")

/** Shows [remoteNote], and follows newly activated values. */
@Composable
internal fun RemoteNoteRow() {
    val values = getKoin().get<RemoteValues>()
    val note by remember(values) { values.changes.map { remoteNote(values) } }.collectAsState(remoteNote(values))
    SettingsValueRow(
        label = stringResource(R.string.showcase_remote_note),
        value = note,
        onClick = null,
        modifier = Modifier.testTag("showcase.remoteNote"),
    )
}
