package com.whitelabel.feature.authentication.presentation.forgot

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whitelabel.core.common.onFailure
import com.whitelabel.core.common.onSuccess
import com.whitelabel.core.presentation.UiText
import com.whitelabel.core.presentation.toUiText
import com.whitelabel.feature.authentication.domain.AuthValidator
import com.whitelabel.feature.authentication.domain.PasswordResetDataSource
import com.whitelabel.feature.authentication.presentation.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ForgotPasswordState(
    val email: String = "",
    val emailError: UiText? = null,
    val error: UiText? = null,
    val isSubmitting: Boolean = false,
    /** The request went through. The text never says whether the account exists. */
    val sent: Boolean = false,
)

sealed interface ForgotPasswordAction {
    data class OnEmailChange(val email: String) : ForgotPasswordAction
    data object OnSubmit : ForgotPasswordAction
}

private const val KEY_EMAIL = "email"

class ForgotPasswordViewModel(
    private val source: PasswordResetDataSource,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(ForgotPasswordState(email = savedState[KEY_EMAIL] ?: ""))
    val state = _state.asStateFlow()

    fun onAction(action: ForgotPasswordAction) {
        when (action) {
            is ForgotPasswordAction.OnEmailChange -> {
                savedState[KEY_EMAIL] = action.email
                _state.update { it.copy(email = action.email, emailError = null, error = null) }
            }
            ForgotPasswordAction.OnSubmit -> submit()
        }
    }

    private fun submit() {
        val current = _state.value
        if (current.isSubmitting) return
        AuthValidator.email(current.email)?.let { error ->
            _state.update { it.copy(emailError = error.toUiText()) }
            return
        }
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            source.requestReset(current.email)
                .onSuccess { _state.update { it.copy(sent = true) } }
                .onFailure { error -> _state.update { it.copy(error = error.toUiText()) } }
            _state.update { it.copy(isSubmitting = false) }
        }
    }
}
