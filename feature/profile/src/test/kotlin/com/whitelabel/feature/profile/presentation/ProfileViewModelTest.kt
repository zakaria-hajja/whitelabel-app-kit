package com.whitelabel.feature.profile.presentation

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.whitelabel.core.common.DataError
import com.whitelabel.core.common.EmptyResult
import com.whitelabel.core.common.Result
import com.whitelabel.core.domain.User
import com.whitelabel.core.domain.UserRepository
import com.whitelabel.core.ui.UiText
import com.whitelabel.feature.profile.R
import com.whitelabel.feature.profile.presentation.profile.ProfileAction
import com.whitelabel.feature.profile.presentation.profile.ProfileViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private class FakeUsers : UserRepository {
    override val user = MutableStateFlow<User?>(User("u1", "ada@example.com", "Ada", null))
    val updates = mutableListOf<Pair<String?, String?>>()
    var answer: suspend () -> EmptyResult<DataError> = { Result.Success(Unit) }
    override suspend fun refresh(): EmptyResult<DataError> = Result.Success(Unit)
    override suspend fun update(firstName: String?, lastName: String?): EmptyResult<DataError> {
        updates += firstName to lastName
        return answer().also { if (it is Result.Success) user.value = user.value?.copy(firstName = firstName, lastName = lastName) }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val users = FakeUsers()

    @BeforeEach fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach fun tearDown() = Dispatchers.resetMain()

    @Test fun `the form starts from the signed-in user`() = runTest {
        val state = ProfileViewModel(users).state.value
        assertThat(state.email).isEqualTo("ada@example.com")
        assertThat(state.firstName).isEqualTo("Ada")
        assertThat(state.lastName).isEqualTo("")
    }

    @Test fun `save is disabled until a valid change`() = runTest {
        val viewModel = ProfileViewModel(users)
        assertThat(viewModel.state.value.canSave).isFalse()
        viewModel.onAction(ProfileAction.OnFirstNameChange("  Ada  ")) // same name once trimmed
        assertThat(viewModel.state.value.canSave).isFalse()
        viewModel.onAction(ProfileAction.OnLastNameChange("Lovelace"))
        assertThat(viewModel.state.value.canSave).isTrue()
    }

    @Test fun `name over fifty characters shows the length error`() = runTest {
        val viewModel = ProfileViewModel(users)
        viewModel.onAction(ProfileAction.OnLastNameChange("a".repeat(51)))
        assertThat(viewModel.state.value.lastNameError).isEqualTo(UiText.Resource(R.string.profile_error_name_long, listOf(50)))
        assertThat(viewModel.state.value.canSave).isFalse()
    }

    @Test fun `saving trims and sends a blank name as cleared`() = runTest {
        val viewModel = ProfileViewModel(users)
        viewModel.onAction(ProfileAction.OnFirstNameChange("   "))
        viewModel.onAction(ProfileAction.OnLastNameChange("  Lovelace "))
        viewModel.onAction(ProfileAction.OnSave)
        assertThat(users.updates).containsExactly(null to "Lovelace")
        assertThat(viewModel.state.value.message).isEqualTo(UiText.Resource(R.string.profile_saved))
        assertThat(viewModel.state.value.canSave).isFalse()
    }

    @Test fun `double save sends one request`() = runTest {
        val pending = CompletableDeferred<Unit>()
        users.answer = { pending.await(); Result.Success(Unit) }
        val viewModel = ProfileViewModel(users)
        viewModel.onAction(ProfileAction.OnLastNameChange("Lovelace"))
        viewModel.onAction(ProfileAction.OnSave)
        viewModel.onAction(ProfileAction.OnSave)
        pending.complete(Unit)
        assertThat(users.updates.size).isEqualTo(1)
    }

    @Test fun `failed save keeps the edits and shows the error`() = runTest {
        users.answer = { Result.Error(DataError.Network.NO_INTERNET) }
        val viewModel = ProfileViewModel(users)
        viewModel.onAction(ProfileAction.OnLastNameChange("Lovelace"))
        viewModel.onAction(ProfileAction.OnSave)
        assertThat(viewModel.state.value.lastName).isEqualTo("Lovelace")
        assertThat(viewModel.state.value.message).isNotNull()
        assertThat(viewModel.state.value.canSave).isTrue()
    }

    @Test fun `an update of the cached user does not overwrite edits in progress`() = runTest {
        val viewModel = ProfileViewModel(users)
        viewModel.onAction(ProfileAction.OnLastNameChange("Lovelace"))
        users.user.value = User("u1", "ada@example.com", "Ada", "King") // a background refresh
        assertThat(viewModel.state.value.lastName).isEqualTo("Lovelace")
        assertThat(viewModel.state.value.firstNameError).isNull()
    }

    @Test fun `text typed while saving stays and is still unsaved`() = runTest {
        val pending = CompletableDeferred<Unit>()
        users.answer = { pending.await(); Result.Success(Unit) }
        val viewModel = ProfileViewModel(users)
        viewModel.onAction(ProfileAction.OnLastNameChange("Lovelace"))
        viewModel.onAction(ProfileAction.OnSave)
        viewModel.onAction(ProfileAction.OnLastNameChange("Lovelace-King")) // typed during the save
        pending.complete(Unit)
        val state = viewModel.state.value
        assertThat(state.lastName).isEqualTo("Lovelace-King")
        assertThat(state.savedLastName).isEqualTo("Lovelace")
        assertThat(state.canSave).isTrue()
    }

    @Test fun `another user signing in resets the form`() = runTest {
        val viewModel = ProfileViewModel(users)
        viewModel.onAction(ProfileAction.OnLastNameChange("Lovelace")) // A's unsaved edit
        users.user.value = null // A signs out
        users.user.value = User("u2", "grace@example.com", "Grace", "Hopper") // B signs in
        val state = viewModel.state.value
        assertThat(state.email).isEqualTo("grace@example.com")
        assertThat(state.firstName).isEqualTo("Grace")
        assertThat(state.lastName).isEqualTo("Hopper")
        assertThat(state.canSave).isFalse()
    }
}
