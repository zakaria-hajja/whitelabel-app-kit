package com.whitelabel.apps.globex.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whitelabel.core.designsystem.component.AppCard
import com.whitelabel.core.designsystem.component.AppPrimaryButton
import com.whitelabel.core.designsystem.component.AppScaffold
import com.whitelabel.core.designsystem.component.AppTextField
import com.whitelabel.core.designsystem.component.EmptyState
import com.whitelabel.core.designsystem.theme.AppTheme

@Composable
internal fun NotesRoot(viewModel: NotesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    NotesScreen(state = state, onAction = viewModel::onAction)
}

@Composable
internal fun NotesScreen(state: NotesState, onAction: (NotesAction) -> Unit) {
    AppScaffold(title = stringResource(R.string.notes_title)) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(AppTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm),
        ) {
            AppTextField(
                value = state.draft,
                onValueChange = { onAction(NotesAction.OnDraftChange(it)) },
                label = stringResource(R.string.notes_draft_label),
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
            )
            AppPrimaryButton(
                text = stringResource(R.string.notes_add),
                onClick = { onAction(NotesAction.OnAddClick) },
                enabled = state.draft.isNotBlank(),
            )
            if (state.notes.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.notes_empty_title),
                    message = stringResource(R.string.notes_empty_message),
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm),
                ) {
                    items(state.notes, key = { it.id }) { note ->
                        NoteItem(note = note, onDelete = { onAction(NotesAction.OnDeleteClick(note.id)) })
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteItem(note: Note, onDelete: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = note.text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.notes_delete))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NotesScreenPreview() {
    AppTheme(darkTheme = false) {
        NotesScreen(
            state = NotesState(notes = listOf(Note("1", "Call the bank", 0), Note("2", "Buy milk", 0))),
            onAction = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NotesScreenEmptyDarkPreview() {
    AppTheme(darkTheme = true) {
        NotesScreen(state = NotesState(), onAction = {})
    }
}
