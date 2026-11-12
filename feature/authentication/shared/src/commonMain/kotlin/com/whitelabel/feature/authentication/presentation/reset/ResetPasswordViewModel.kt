package com.whitelabel.feature.authentication.presentation.reset

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whitelabel.core.common.ApiErrorCode
import com.whitelabel.core.common.DataError
import com.whitelabel.core.common.onFailure
import com.whitelabel.core.common.onSuccess
import com.whitelabel.core.domain.PasswordPolicy
import com.whitelabel.core.presentation.UiText
import com.whitelabel.core.presentation.toUiText
import com.whitelabel.feature.authentication.domain.PasswordResetDataSource
import com.whitelabel.feature.authentication.presentation.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ResetPasswordState(
    val password: String = "",
    val passwordError: UiText? = null,
    val error: UiText? = null,
    val isSubmitting: Boolean = false,
    /** The link is expired or used: offer a new one instead of the form. */
    val tokenExpired: Boolean = false,
    val done: Boolean = false,
) {
    val canSubmit: Boolean get() = password.isNotEmpty() && !isSubmitting
}

sealed interface ResetPasswordAction {
    data class OnPasswordChange(val password: String) : ResetPasswordAction
    data object OnSubmit : ResetPasswordAction
}

/** Opened from the emailed link; the token is the route's `token` argument. */
class ResetPasswordViewModel(
    private val source: PasswordResetDataSource,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val token: String = savedState.get<String>("token").orEmpty()

    private val _state = MutableStateFlow(ResetPasswordState())
    val state = _state.asStateFlow()

    fun onAction(action: ResetPasswordAction) {
        when (action) {
            is ResetPasswordAction.OnPasswordChange -> _state.update { it.copy(password = action.password, passwordError = null) }
            ResetPasswordAction.OnSubmit -> submit()
        }
    }

    private fun submit() {
        val current = _state.value
        if (current.isSubmitting || current.done) return
        PasswordPolicy.check(current.password)?.let { error ->
            _state.update { it.copy(passwordError = error.toUiText()) }
            return
        }
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            source.reset(token, current.password)
                .onSuccess { _state.update { it.copy(done = true, password = "") } }
                .onFailure { error ->
                    if (error is DataError.Api && error.code == ApiErrorCode.INVALID_RESET_TOKEN) {
                        _state.update { it.copy(tokenExpired = true) }
                    } else {
                        _state.update { it.copy(error = error.toUiText()) }
                    }
                }
            _state.update { it.copy(isSubmitting = false) }
        }
    }
}
