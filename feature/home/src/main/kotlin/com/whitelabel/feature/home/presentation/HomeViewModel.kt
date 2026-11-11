package com.whitelabel.feature.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whitelabel.core.common.onFailure
import com.whitelabel.core.domain.UserRepository
import com.whitelabel.core.ui.UiText
import com.whitelabel.core.ui.toUiText
import com.whitelabel.feature.home.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class HomeState(
    val greeting: UiText = UiText.Resource(R.string.home_greeting),
    val isRefreshing: Boolean = false,
    /** A one-off message (why a refresh failed). */
    val message: UiText? = null,
)

internal sealed interface HomeAction {
    data object OnRefresh : HomeAction
    data object OnMessageShown : HomeAction
}

/** Greets the cached user; pull to refresh updates them from the backend. */
@HiltViewModel
internal class HomeViewModel @Inject constructor(
    private val users: UserRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            users.user.collect { user ->
                val name = user?.firstName?.trim().orEmpty()
                val greeting = if (name.isEmpty()) UiText.Resource(R.string.home_greeting) else UiText.Resource(R.string.home_greeting_named, listOf(name))
                _state.update { it.copy(greeting = greeting) }
            }
        }
    }

    fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.OnRefresh -> refresh()
            HomeAction.OnMessageShown -> _state.update { it.copy(message = null) }
        }
    }

    private fun refresh() {
        if (_state.value.isRefreshing) return
        _state.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            users.refresh().onFailure { error -> _state.update { it.copy(message = error.toUiText()) } }
            _state.update { it.copy(isRefreshing = false) }
        }
    }
}
