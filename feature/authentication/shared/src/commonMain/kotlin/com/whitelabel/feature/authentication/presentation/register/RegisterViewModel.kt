package com.whitelabel.feature.authentication.presentation.register

import com.whitelabel.feature.authentication.shared.resources.Res
import com.whitelabel.feature.authentication.shared.resources.auth_error_email_taken
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whitelabel.core.analytics.Analytics
import com.whitelabel.core.analytics.AnalyticsEvent
import com.whitelabel.core.analytics.AnalyticsEvents
import com.whitelabel.core.common.ApiErrorCode
import com.whitelabel.core.common.DataError
import com.whitelabel.core.common.onFailure
import com.whitelabel.core.common.onSuccess
import com.whitelabel.core.domain.NamePolicy
import com.whitelabel.core.domain.PasswordPolicy
import com.whitelabel.core.domain.Registration
import com.whitelabel.core.domain.SessionRepository
import com.whitelabel.core.presentation.UiText
import com.whitelabel.feature.authentication.domain.AuthValidator
import com.whitelabel.feature.authentication.presentation.toAuthUiText
import com.whitelabel.feature.authentication.presentation.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegisterState(
    val email: String = "",
    val password: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val emailError: UiText? = null,
    val passwordError: UiText? = null,
    val firstNameError: UiText? = null,
    val lastNameError: UiText? = null,
    val error: UiText? = null,
    val isSubmitting: Boolean = false,
) {
    val canSubmit: Boolean get() = email.isNotBlank() && password.isNotEmpty() && !isSubmitting
}

sealed interface RegisterAction {
    data class OnEmailChange(val email: String) : RegisterAction
    data class OnPasswordChange(val password: String) : RegisterAction
    data class OnFirstNameChange(val name: String) : RegisterAction
    data class OnLastNameChange(val name: String) : RegisterAction
    data object OnSubmit : RegisterAction
}

private const val KEY_EMAIL = "email"
private const val KEY_FIRST = "firstName"
private const val KEY_LAST = "lastName"

/** Registration signs in too; the shell then shows the app. */
class RegisterViewModel(
    private val session: SessionRepository,
    private val analytics: Analytics,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(
        RegisterState(
            email = savedState[KEY_EMAIL] ?: "",
            firstName = savedState[KEY_FIRST] ?: "",
            lastName = savedState[KEY_LAST] ?: "",
        ),
    )
    val state = _state.asStateFlow()

    fun onAction(action: RegisterAction) {
        when (action) {
            is RegisterAction.OnEmailChange -> {
                savedState[KEY_EMAIL] = action.email
                _state.update { it.copy(email = action.email, emailError = null, error = null) }
            }
            is RegisterAction.OnPasswordChange -> _state.update { it.copy(password = action.password, passwordError = null) }
            is RegisterAction.OnFirstNameChange -> {
                savedState[KEY_FIRST] = action.name
                _state.update { it.copy(firstName = action.name, firstNameError = null) }
            }
            is RegisterAction.OnLastNameChange -> {
                savedState[KEY_LAST] = action.name
                _state.update { it.copy(lastName = action.name, lastNameError = null) }
            }
            RegisterAction.OnSubmit -> submit()
        }
    }

    private fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        val checked = current.copy(
            emailError = AuthValidator.email(current.email)?.toUiText(),
            passwordError = PasswordPolicy.check(current.password)?.toUiText(),
            firstNameError = NamePolicy.check(current.firstName)?.toUiText(),
            lastNameError = NamePolicy.check(current.lastName)?.toUiText(),
        )
        if (listOf(checked.emailError, checked.passwordError, checked.firstNameError, checked.lastNameError).any { it != null }) {
            _state.value = checked
            return
        }
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            val registration = Registration(
                email = current.email,
                password = current.password,
                firstName = current.firstName.trim().ifEmpty { null },
                lastName = current.lastName.trim().ifEmpty { null },
            )
            session.register(registration)
                .onSuccess { analytics.track(AnalyticsEvent(AnalyticsEvents.SIGN_UP, mapOf("method" to "password"))) }
                .onFailure { error -> _state.update { it.withError(error) } }
            _state.update { it.copy(isSubmitting = false) }
        }
    }

    private fun RegisterState.withError(error: DataError): RegisterState =
        if (error is DataError.Api && error.code == ApiErrorCode.EMAIL_ALREADY_REGISTERED) {
            copy(emailError = UiText.Resource(Res.string.auth_error_email_taken))
        } else {
            copy(error = error.toAuthUiText())
        }
}
