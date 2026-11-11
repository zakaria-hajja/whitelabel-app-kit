package com.whitelabel.feature.home.presentation

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import com.whitelabel.core.common.DataError
import com.whitelabel.core.common.EmptyResult
import com.whitelabel.core.common.Result
import com.whitelabel.core.domain.User
import com.whitelabel.core.domain.UserRepository
import com.whitelabel.core.ui.UiText
import com.whitelabel.feature.home.R
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

private class FakeUsers(first: String?) : UserRepository {
    override val user = MutableStateFlow<User?>(User("u1", "ada@example.com", first, null))
    var refreshAnswer: EmptyResult<DataError> = Result.Success(Unit)
    var refreshes = 0
    override suspend fun refresh(): EmptyResult<DataError> { refreshes++; return refreshAnswer }
    override suspend fun update(firstName: String?, lastName: String?): EmptyResult<DataError> = Result.Success(Unit)
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @BeforeEach fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach fun tearDown() = Dispatchers.resetMain()

    @Test fun `greets by first name`() = runTest {
        val state = HomeViewModel(FakeUsers("Ada")).state.value
        assertThat(state.greeting).isEqualTo(UiText.Resource(R.string.home_greeting_named, listOf("Ada")))
    }

    @Test fun `blank first name uses the plain greeting`() = runTest {
        assertThat(HomeViewModel(FakeUsers("   ")).state.value.greeting).isEqualTo(UiText.Resource(R.string.home_greeting))
        assertThat(HomeViewModel(FakeUsers(null)).state.value.greeting).isEqualTo(UiText.Resource(R.string.home_greeting))
    }

    @Test fun `refresh error shows a message and keeps the cached user`() = runTest {
        val users = FakeUsers("Ada").apply { refreshAnswer = Result.Error(DataError.Network.NO_INTERNET) }
        val viewModel = HomeViewModel(users)
        viewModel.onAction(HomeAction.OnRefresh)
        val state = viewModel.state.value
        assertThat(users.refreshes).isEqualTo(1)
        assertThat(state.message).isNotNull()
        assertThat(state.isRefreshing).isFalse()
        assertThat(state.greeting).isEqualTo(UiText.Resource(R.string.home_greeting_named, listOf("Ada")))
    }
}
