package com.whitelabel.feature.profile.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whitelabel.core.common.onFailure
import com.whitelabel.core.common.onSuccess
import com.whitelabel.core.domain.NamePolicy
import com.whitelabel.core.domain.UserRepository
import com.whitelabel.core.ui.UiText
import com.whitelabel.core.ui.toUiText
import com.whitelabel.feature.profile.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class ProfileState(
    val loaded: Boolean = false,
    val email: String = "",
    val firstName: String = "",
    val lastName: String = "",
    /** The names as the backend has them, to tell whether there is anything to save. */
    val savedFirstName: String = "",
    val savedLastName: String = "",
    val firstNameError: UiText? = null,
    val lastNameError: UiText? = null,
    val isSaving: Boolean = false,
    /** A one-off message: saved, or why saving failed. */
    val message: UiText? = null,
) {
    val isDirty: Boolean get() = firstName.trim() != savedFirstName || lastName.trim() != savedLastName
    val canSave: Boolean get() = loaded && isDirty && firstNameError == null && lastNameError == null && !isSaving
}

internal sealed interface ProfileAction {
    data class OnFirstNameChange(val name: String) : ProfileAction
    data class OnLastNameChange(val name: String) : ProfileAction
    data object OnSave : ProfileAction
    data object OnMessageShown : ProfileAction
}

/** Edits the signed-in user's names. The email is shown, not edited. */
@HiltViewModel
internal class ProfileViewModel @Inject constructor(
    private val users: UserRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            users.user.filterNotNull().collect { user ->
                _state.update {
                    val saved = it.copy(savedFirstName = user.firstName.orEmpty(), savedLastName = user.lastName.orEmpty(), email = user.email)
                    // Edits in progress win over a background refresh of the cached user.
                    if (it.loaded && it.isDirty) saved else saved.copy(loaded = true, firstName = saved.savedFirstName, lastName = saved.savedLastName)
                }
            }
        }
    }

    fun onAction(action: ProfileAction) {
        when (action) {
            is ProfileAction.OnFirstNameChange ->
                _state.update { it.copy(firstName = action.name, firstNameError = NamePolicy.check(action.name)?.let { nameTooLong() }) }
            is ProfileAction.OnLastNameChange ->
                _state.update { it.copy(lastName = action.name, lastNameError = NamePolicy.check(action.name)?.let { nameTooLong() }) }
            ProfileAction.OnSave -> save()
            ProfileAction.OnMessageShown -> _state.update { it.copy(message = null) }
        }
    }

    private fun save() {
        val current = _state.value
        if (!current.canSave) return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            users.update(firstName = current.firstName.trim().ifEmpty { null }, lastName = current.lastName.trim().ifEmpty { null })
                .onSuccess {
                    _state.update {
                        it.copy(
                            firstName = it.firstName.trim(),
                            lastName = it.lastName.trim(),
                            savedFirstName = it.firstName.trim(),
                            savedLastName = it.lastName.trim(),
                            message = UiText.Resource(R.string.profile_saved),
                        )
                    }
                }
                .onFailure { error -> _state.update { it.copy(message = error.toUiText()) } }
            _state.update { it.copy(isSaving = false) }
        }
    }

    private fun nameTooLong() = UiText.Resource(R.string.profile_error_name_long, listOf(NamePolicy.MAX))
}
