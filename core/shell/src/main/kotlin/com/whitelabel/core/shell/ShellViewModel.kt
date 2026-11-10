package com.whitelabel.core.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whitelabel.core.branding.BrandConfig
import com.whitelabel.core.domain.AppConfigRepository
import com.whitelabel.core.domain.SessionRepository
import com.whitelabel.core.domain.SessionState
import com.whitelabel.core.domain.ThemePreference
import com.whitelabel.core.domain.UserPreferencesRepository
import com.whitelabel.core.domain.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** How long a cold start waits for the remote config before using the cached one. */
internal const val CONFIG_WAIT_MS = 2_000L

internal data class ShellState(
    /** The tabs are decided; until then the splash stays on. */
    val ready: Boolean = false,
    val visibleTabs: List<Tab> = emptyList(),
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val session: SessionState = SessionState.Unknown,
)

/**
 * The shell's runtime state. The visible tabs are decided once per process, after the remote
 * config arrives or [CONFIG_WAIT_MS] passes, so tabs never disappear while the app is in use; a
 * config that arrives later is cached and applies on the next launch.
 */
@HiltViewModel
internal class ShellViewModel @Inject constructor(
    private val definition: AppDefinition,
    private val brand: BrandConfig,
    private val appConfig: AppConfigRepository,
    preferences: UserPreferencesRepository,
    session: SessionRepository,
    private val users: UserRepository,
) : ViewModel() {

    private val tabs = MutableStateFlow<List<Tab>?>(null)

    val state: StateFlow<ShellState> = combine(tabs, preferences.theme, appConfig.config, session.state) {
            tabs, theme, config, sessionState ->
        ShellState(
            ready = tabs != null,
            visibleTabs = tabs.orEmpty(),
            theme = theme ?: config.defaultTheme,
            session = sessionState,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ShellState())

    init {
        viewModelScope.launch {
            val refresh = launch { appConfig.refresh() }
            withTimeoutOrNull(CONFIG_WAIT_MS) { refresh.join() }
            tabs.value = definition.visibleTabs(appConfig.config.value.features, brand.runtimeDefaults.enabledFeatures)
        }
        viewModelScope.launch {
            if (session.state.first { it != SessionState.Unknown } == SessionState.SignedIn) users.refresh()
        }
    }
}
