package com.whitelabel.feature.profile.presentation

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.whitelabel.core.common.ApiErrorCode
import com.whitelabel.core.common.DataError
import com.whitelabel.core.common.EmptyResult
import com.whitelabel.core.common.Result
import com.whitelabel.core.ui.UiText
import com.whitelabel.feature.profile.R
import com.whitelabel.feature.profile.domain.PasswordDataSource
import com.whitelabel.feature.profile.presentation.password.ChangePasswordAction
import com.whitelabel.feature.profile.presentation.password.ChangePasswordViewModel
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

private class FakePasswords : PasswordDataSource {
    val changes = mutableListOf<Pair<String, String>>()
    var answer: suspend () -> EmptyResult<DataError> = { Result.Success(Unit) }
    override suspend fun change(current: String, new: String): EmptyResult<DataError> {
        changes += current to new
        return answer()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChangePasswordViewModelTest {

    private val passwords = FakePasswords()

    @BeforeEach fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach fun tearDown() = Dispatchers.resetMain()

    private fun ChangePasswordViewModel.fill(current: String = "old pass", new: String = "0123456789", confirm: String = new) {
        onAction(ChangePasswordAction.OnCurrentChange(current))
        onAction(ChangePasswordAction.OnNewChange(new))
        onAction(ChangePasswordAction.OnConfirmChange(confirm))
    }

    @Test fun `mismatched confirmation blocks submit`() = runTest {
        val viewModel = ChangePasswordViewModel(passwords)
        viewModel.fill(confirm = "0123456780")
        viewModel.onAction(ChangePasswordAction.OnSubmit)
        assertThat(viewModel.state.value.confirmError).isEqualTo(UiText.Resource(R.string.profile_error_confirm_mismatch))
        assertThat(passwords.changes).containsExactly()
    }

    @Test fun `a short new password is caught before sending`() = runTest {
        val viewModel = ChangePasswordViewModel(passwords)
        viewModel.fill(new = "short")
        viewModel.onAction(ChangePasswordAction.OnSubmit)
        assertThat(viewModel.state.value.newError).isEqualTo(UiText.Resource(R.string.profile_error_password_short, listOf(10)))
        assertThat(passwords.changes).containsExactly()
    }

    @Test fun `wrong current password shows its message`() = runTest {
        passwords.answer = { Result.Error(DataError.Api(ApiErrorCode.INVALID_CREDENTIALS, 401)) }
        val viewModel = ChangePasswordViewModel(passwords)
        viewModel.fill()
        viewModel.onAction(ChangePasswordAction.OnSubmit)
        assertThat(viewModel.state.value.currentError).isEqualTo(UiText.Resource(R.string.profile_error_wrong_current_password))
        assertThat(viewModel.state.value.done).isFalse()
    }

    @Test fun `double submit sends one request`() = runTest {
        val pending = CompletableDeferred<Unit>()
        passwords.answer = { pending.await(); Result.Success(Unit) }
        val viewModel = ChangePasswordViewModel(passwords)
        viewModel.fill()
        viewModel.onAction(ChangePasswordAction.OnSubmit)
        viewModel.onAction(ChangePasswordAction.OnSubmit)
        pending.complete(Unit)
        assertThat(passwords.changes.size).isEqualTo(1)
    }

    @Test fun `success is done and forgets the passwords`() = runTest {
        val viewModel = ChangePasswordViewModel(passwords)
        viewModel.fill()
        viewModel.onAction(ChangePasswordAction.OnSubmit)
        val state = viewModel.state.value
        assertThat(state.done).isTrue()
        assertThat(state.current).isEqualTo("")
        assertThat(state.new).isEqualTo("")
        assertThat(state.error).isNull()
    }
}
