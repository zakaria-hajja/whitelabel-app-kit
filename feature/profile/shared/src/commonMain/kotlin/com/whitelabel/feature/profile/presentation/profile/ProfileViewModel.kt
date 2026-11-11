package com.whitelabel.feature.profile.presentation.profile

import com.whitelabel.feature.profile.shared.resources.Res
import com.whitelabel.feature.profile.shared.resources.profile_error_name_long
import com.whitelabel.feature.profile.shared.resources.profile_saved
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whitelabel.core.common.onFailure
import com.whitelabel.core.common.onSuccess
import com.whitelabel.core.domain.NamePolicy
import com.whitelabel.core.domain.UserRepository
import com.whitelabel.core.presentation.UiText
import com.whitelabel.core.presentation.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileState(
    val loaded: Boolean = false,
    /** Whose profile the form holds: a different user signing in resets it. */
    val userId: String? = null,
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

sealed interface ProfileAction {
    data class OnFirstNameChange(val name: String) : ProfileAction
    data class OnLastNameChange(val name: String) : ProfileAction
    data object OnSave : ProfileAction
    data object OnMessageShown : ProfileAction
}

/** Edits the signed-in user's names. The email is shown, not edited. */
class ProfileViewModel(
    private val users: UserRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            users.user.filterNotNull().collect { user ->
                _state.update {
                    val sameUser = it.userId == null || it.userId == user.id
                    val saved = it.copy(
                        userId = user.id,
                        email = user.email,
                        savedFirstName = user.firstName.orEmpty(),
                        savedLastName = user.lastName.orEmpty(),
                    )
                    // The same user's edits in progress win over a background refresh; another user's never carry over.
                    if (sameUser && it.loaded && it.isDirty) {
                        saved
                    } else {
                        saved.copy(
                            loaded = true,
                            firstName = saved.savedFirstName,
                            lastName = saved.savedLastName,
                            firstNameError = null,
                            lastNameError = null,
                        )
                    }
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
        val sentFirst = current.firstName.trim()
        val sentLast = current.lastName.trim()
        viewModelScope.launch {
            users.update(firstName = sentFirst.ifEmpty { null }, lastName = sentLast.ifEmpty { null })
                .onSuccess {
                    _state.update {
                        // What was sent is saved; text typed while saving stays, and stays unsaved.
                        it.copy(
                            firstName = if (it.firstName == current.firstName) sentFirst else it.firstName,
                            lastName = if (it.lastName == current.lastName) sentLast else it.lastName,
                            savedFirstName = sentFirst,
                            savedLastName = sentLast,
                            message = UiText.Resource(Res.string.profile_saved),
                        )
                    }
                }
                .onFailure { error -> _state.update { it.copy(message = error.toUiText()) } }
            _state.update { it.copy(isSaving = false) }
        }
    }

    private fun nameTooLong() = UiText.Resource(Res.string.profile_error_name_long, listOf(NamePolicy.MAX))
}
