package com.whitelabel.feature.authentication.presentation.login

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whitelabel.core.analytics.Analytics
import com.whitelabel.core.analytics.AnalyticsEvent
import com.whitelabel.core.analytics.AnalyticsEvents
import com.whitelabel.core.common.onFailure
import com.whitelabel.core.common.onSuccess
import com.whitelabel.core.domain.SessionRepository
import com.whitelabel.core.ui.UiText
import com.whitelabel.feature.authentication.domain.AuthValidator
import com.whitelabel.feature.authentication.presentation.toAuthUiText
import com.whitelabel.feature.authentication.presentation.toUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class LoginState(
    val email: String = "",
    val password: String = "",
    val emailError: UiText? = null,
    val error: UiText? = null,
    val isSubmitting: Boolean = false,
) {
    val canSubmit: Boolean get() = email.isNotBlank() && password.isNotEmpty() && !isSubmitting
}

internal sealed interface LoginAction {
    data class OnEmailChange(val email: String) : LoginAction
    data class OnPasswordChange(val password: String) : LoginAction
    data object OnSubmit : LoginAction
}

private const val KEY_EMAIL = "email"

/** Signing in flips the session; the shell then shows the app, so no navigation happens here. */
@HiltViewModel
internal class LoginViewModel @Inject constructor(
    private val session: SessionRepository,
    private val analytics: Analytics,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    // The email survives process death; the password deliberately doesn't.
    private val _state = MutableStateFlow(LoginState(email = savedState[KEY_EMAIL] ?: ""))
    val state = _state.asStateFlow()

    fun onAction(action: LoginAction) {
        when (action) {
            is LoginAction.OnEmailChange -> {
                savedState[KEY_EMAIL] = action.email
                _state.update { it.copy(email = action.email, emailError = null, error = null) }
            }
            is LoginAction.OnPasswordChange -> _state.update { it.copy(password = action.password, error = null) }
            LoginAction.OnSubmit -> submit()
        }
    }

    private fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        AuthValidator.email(current.email)?.let { error ->
            _state.update { it.copy(emailError = error.toUiText()) }
            return
        }
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            session.signIn(current.email, current.password)
                .onSuccess { analytics.track(AnalyticsEvent(AnalyticsEvents.LOGIN, mapOf("method" to "password"))) }
                .onFailure { error -> _state.update { it.copy(error = error.toAuthUiText()) } }
            _state.update { it.copy(isSubmitting = false) }
        }
    }
}
