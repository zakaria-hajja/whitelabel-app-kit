package com.whitelabel.apps.globex.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class NotesState(
    val notes: List<Note> = emptyList(),
    val draft: String = "",
)

internal sealed interface NotesAction {
    data class OnDraftChange(val text: String) : NotesAction
    data object OnAddClick : NotesAction
    data class OnDeleteClick(val id: String) : NotesAction
}

@HiltViewModel
internal class NotesViewModel @Inject constructor(
    private val store: NotesStore,
) : ViewModel() {

    private val draft = MutableStateFlow("")

    val state: StateFlow<NotesState> = combine(store.notes, draft) { notes, draft -> NotesState(notes, draft) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesState())

    fun onAction(action: NotesAction) {
        when (action) {
            is NotesAction.OnDraftChange -> draft.value = action.text
            NotesAction.OnAddClick -> add()
            is NotesAction.OnDeleteClick -> viewModelScope.launch { store.delete(action.id) }
        }
    }

    private fun add() {
        val text = draft.value.trim()
        if (text.isEmpty()) return
        draft.update { "" }
        viewModelScope.launch { store.add(text) }
    }
}
