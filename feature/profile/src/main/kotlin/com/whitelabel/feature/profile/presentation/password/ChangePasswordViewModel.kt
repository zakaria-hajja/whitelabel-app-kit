package com.whitelabel.feature.profile.presentation.password

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whitelabel.core.common.ApiErrorCode
import com.whitelabel.core.common.DataError
import com.whitelabel.core.common.onFailure
import com.whitelabel.core.common.onSuccess
import com.whitelabel.core.domain.PasswordError
import com.whitelabel.core.domain.PasswordPolicy
import com.whitelabel.core.ui.UiText
import com.whitelabel.core.ui.toUiText
import com.whitelabel.feature.profile.R
import com.whitelabel.feature.profile.domain.PasswordDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class ChangePasswordState(
    val current: String = "",
    val new: String = "",
    val confirm: String = "",
    val currentError: UiText? = null,
    val newError: UiText? = null,
    val confirmError: UiText? = null,
    val error: UiText? = null,
    val isSubmitting: Boolean = false,
    /** Changed; the other devices were signed out. */
    val done: Boolean = false,
) {
    val canSubmit: Boolean get() = current.isNotEmpty() && new.isNotEmpty() && confirm.isNotEmpty() && !isSubmitting && !done
}

internal sealed interface ChangePasswordAction {
    data class OnCurrentChange(val password: String) : ChangePasswordAction
    data class OnNewChange(val password: String) : ChangePasswordAction
    data class OnConfirmChange(val password: String) : ChangePasswordAction
    data object OnSubmit : ChangePasswordAction
}

/** Passwords are never trimmed and never saved (no SavedStateHandle). */
@HiltViewModel
internal class ChangePasswordViewModel @Inject constructor(
    private val passwords: PasswordDataSource,
) : ViewModel() {

    private val _state = MutableStateFlow(ChangePasswordState())
    val state = _state.asStateFlow()

    fun onAction(action: ChangePasswordAction) {
        when (action) {
            is ChangePasswordAction.OnCurrentChange -> _state.update { it.copy(current = action.password, currentError = null, error = null) }
            is ChangePasswordAction.OnNewChange -> _state.update { it.copy(new = action.password, newError = null, confirmError = null) }
            is ChangePasswordAction.OnConfirmChange -> _state.update { it.copy(confirm = action.password, confirmError = null) }
            ChangePasswordAction.OnSubmit -> submit()
        }
    }

    private fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        val newError = PasswordPolicy.check(current.new)?.toUiText()
        val confirmError = UiText.Resource(R.string.profile_error_confirm_mismatch).takeIf { current.confirm != current.new }
        if (newError != null || confirmError != null) {
            _state.update { it.copy(newError = newError, confirmError = confirmError) }
            return
        }
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            passwords.change(current.current, current.new)
                .onSuccess { _state.update { ChangePasswordState(done = true) } }
                .onFailure { error ->
                    if (error is DataError.Api && error.code == ApiErrorCode.INVALID_CREDENTIALS) {
                        _state.update { it.copy(currentError = UiText.Resource(R.string.profile_error_wrong_current_password)) }
                    } else {
                        _state.update { it.copy(error = error.toUiText()) }
                    }
                }
            _state.update { it.copy(isSubmitting = false) }
        }
    }

    private fun PasswordError.toUiText(): UiText = when (this) {
        PasswordError.TOO_SHORT -> UiText.Resource(R.string.profile_error_password_short, listOf(PasswordPolicy.MIN))
        PasswordError.TOO_LONG -> UiText.Resource(R.string.profile_error_password_long, listOf(PasswordPolicy.MAX))
    }
}
