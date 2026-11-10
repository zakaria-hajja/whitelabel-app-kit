package com.whitelabel.feature.authentication.presentation

import androidx.lifecycle.SavedStateHandle
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.whitelabel.core.analytics.AnalyticsEvent
import com.whitelabel.core.analytics.AnalyticsEvents
import com.whitelabel.core.common.ApiErrorCode
import com.whitelabel.core.common.DataError
import com.whitelabel.core.common.Result
import com.whitelabel.core.ui.UiText
import com.whitelabel.feature.authentication.R
import com.whitelabel.feature.authentication.presentation.forgot.ForgotPasswordAction
import com.whitelabel.feature.authentication.presentation.forgot.ForgotPasswordViewModel
import com.whitelabel.feature.authentication.presentation.login.LoginAction
import com.whitelabel.feature.authentication.presentation.login.LoginViewModel
import com.whitelabel.feature.authentication.presentation.register.RegisterAction
import com.whitelabel.feature.authentication.presentation.register.RegisterViewModel
import com.whitelabel.feature.authentication.presentation.reset.ResetPasswordAction
import com.whitelabel.feature.authentication.presentation.reset.ResetPasswordViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelsTest {

    private val session = FakeSession()
    private val analytics = FakeAnalytics()
    private val reset = FakePasswordReset()

    @BeforeEach fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach fun tearDown() = Dispatchers.resetMain()

    private fun LoginViewModel.fill(email: String = "ada@example.com", password: String = "secret pass") {
        onAction(LoginAction.OnEmailChange(email))
        onAction(LoginAction.OnPasswordChange(password))
    }

    @Test fun `double submit sends one sign in`() = runTest {
        val pending = CompletableDeferred<Unit>()
        session.answer = { pending.await(); Result.Success(Unit) }
        val viewModel = LoginViewModel(session, analytics, SavedStateHandle())
        viewModel.fill()
        viewModel.onAction(LoginAction.OnSubmit)
        assertThat(viewModel.state.value.isSubmitting).isTrue()
        viewModel.onAction(LoginAction.OnSubmit)
        pending.complete(Unit)
        assertThat(session.signIns).isEqualTo(1)
        assertThat(viewModel.state.value.isSubmitting).isFalse()
    }

    @Test fun `invalid email is caught before any request`() = runTest {
        val viewModel = LoginViewModel(session, analytics, SavedStateHandle())
        viewModel.fill(email = "ada@")
        viewModel.onAction(LoginAction.OnSubmit)
        assertThat(viewModel.state.value.emailError).isEqualTo(UiText.Resource(R.string.auth_error_email_invalid))
        assertThat(session.signIns).isEqualTo(0)
    }

    @Test fun `invalid credentials shows the wrong password message`() = runTest {
        session.answer = { Result.Error(DataError.Api(ApiErrorCode.INVALID_CREDENTIALS, 401)) }
        val viewModel = LoginViewModel(session, analytics, SavedStateHandle())
        viewModel.fill()
        viewModel.onAction(LoginAction.OnSubmit)
        assertThat(viewModel.state.value.error).isEqualTo(UiText.Resource(R.string.auth_error_invalid_credentials))
    }

    @Test fun `password is not kept in saved state`() = runTest {
        val saved = SavedStateHandle()
        LoginViewModel(session, analytics, saved).fill(email = "ada@example.com", password = "secret pass")
        assertThat(saved.keys().map { saved.get<Any>(it) }).doesNotContain("secret pass")
        val restored = LoginViewModel(session, analytics, saved)
        assertThat(restored.state.value.email).isEqualTo("ada@example.com")
        assertThat(restored.state.value.password).isEqualTo("")
    }

    @Test fun `analytics never receives the email`() = runTest {
        val viewModel = LoginViewModel(session, analytics, SavedStateHandle())
        viewModel.fill()
        viewModel.onAction(LoginAction.OnSubmit)
        assertThat(analytics.events).containsExactly(AnalyticsEvent(AnalyticsEvents.LOGIN, mapOf("method" to "password")))
    }

    @Test fun `already registered email marks the email field`() = runTest {
        session.answer = { Result.Error(DataError.Api(ApiErrorCode.EMAIL_ALREADY_REGISTERED, 409)) }
        val viewModel = RegisterViewModel(session, analytics, SavedStateHandle())
        viewModel.onAction(RegisterAction.OnEmailChange("ada@example.com"))
        viewModel.onAction(RegisterAction.OnPasswordChange("0123456789"))
        viewModel.onAction(RegisterAction.OnSubmit)
        assertThat(viewModel.state.value.emailError).isEqualTo(UiText.Resource(R.string.auth_error_email_taken))
        assertThat(viewModel.state.value.error).isNull()
    }

    @Test fun `register checks the password and name rules before sending`() = runTest {
        val viewModel = RegisterViewModel(session, analytics, SavedStateHandle())
        viewModel.onAction(RegisterAction.OnEmailChange("ada@example.com"))
        viewModel.onAction(RegisterAction.OnPasswordChange("short"))
        viewModel.onAction(RegisterAction.OnFirstNameChange("a".repeat(51)))
        viewModel.onAction(RegisterAction.OnSubmit)
        assertThat(viewModel.state.value.passwordError).isEqualTo(UiText.Resource(R.string.auth_error_password_short, listOf(10)))
        assertThat(viewModel.state.value.firstNameError).isEqualTo(UiText.Resource(R.string.auth_error_name_long, listOf(50)))
        assertThat(session.registrations).containsExactly()
    }

    @Test fun `successful registration sends blank names as absent and tracks sign up`() = runTest {
        val viewModel = RegisterViewModel(session, analytics, SavedStateHandle())
        viewModel.onAction(RegisterAction.OnEmailChange("ada@example.com"))
        viewModel.onAction(RegisterAction.OnPasswordChange("0123456789"))
        viewModel.onAction(RegisterAction.OnLastNameChange("  "))
        viewModel.onAction(RegisterAction.OnSubmit)
        assertThat(session.registrations.single().lastName).isNull()
        assertThat(analytics.events).containsExactly(AnalyticsEvent(AnalyticsEvents.SIGN_UP, mapOf("method" to "password")))
    }

    @Test fun `forgot password confirms without saying whether the account exists`() = runTest {
        val viewModel = ForgotPasswordViewModel(reset, SavedStateHandle())
        viewModel.onAction(ForgotPasswordAction.OnEmailChange("nobody@example.com"))
        viewModel.onAction(ForgotPasswordAction.OnSubmit)
        assertThat(viewModel.state.value.sent).isTrue()
        assertThat(reset.requests).containsExactly("nobody@example.com")
    }

    @Test fun `expired reset token offers a new link`() = runTest {
        reset.answer = { Result.Error(DataError.Api(ApiErrorCode.INVALID_RESET_TOKEN, 400)) }
        val viewModel = ResetPasswordViewModel(reset, SavedStateHandle(mapOf("token" to "tok")))
        viewModel.onAction(ResetPasswordAction.OnPasswordChange("0123456789"))
        viewModel.onAction(ResetPasswordAction.OnSubmit)
        assertThat(viewModel.state.value.tokenExpired).isTrue()
        assertThat(viewModel.state.value.done).isFalse()
    }

    @Test fun `successful reset is done`() = runTest {
        val viewModel = ResetPasswordViewModel(reset, SavedStateHandle(mapOf("token" to "tok")))
        viewModel.onAction(ResetPasswordAction.OnPasswordChange("0123456789"))
        viewModel.onAction(ResetPasswordAction.OnSubmit)
        assertThat(viewModel.state.value.done).isTrue()
        assertThat(viewModel.state.value.error).isNull()
        assertThat(reset.resets).isEqualTo(1)
        assertThat(viewModel.state.value.passwordError).isNull()
        assertThat(viewModel.state.value).isNotNull()
    }
}
