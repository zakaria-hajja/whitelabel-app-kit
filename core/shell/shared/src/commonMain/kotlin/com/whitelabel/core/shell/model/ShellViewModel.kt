package com.whitelabel.core.shell.model

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whitelabel.core.branding.BrandConfig
import com.whitelabel.core.branding.hasBackend
import com.whitelabel.core.domain.AppConfigRepository
import com.whitelabel.core.domain.SessionRepository
import com.whitelabel.core.domain.SessionState
import com.whitelabel.core.domain.ThemePreference
import com.whitelabel.core.domain.UserPreferencesRepository
import com.whitelabel.core.domain.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** How long a cold start waits for the remote config before using the cached one. */
const val CONFIG_WAIT_MS = 2_000L

private const val KEY_DEEP_LINK = "pendingDeepLink"

data class ShellState(
    /** The tabs are decided; until then the splash stays on. */
    val ready: Boolean = false,
    /** The shared route ids of the visible tabs, in order (`<Name>Routes` constants). */
    val visibleTabRoutes: List<String> = emptyList(),
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val session: SessionState = SessionState.Unknown,
) {
    /**
     * Whether the shell may show the app (and the splash may go): the tabs are decided and, with a
     * sign-in gate, the session is known. Composing the gate earlier would start at sign-in, then
     * switch, which drops a restored back stack and creates screens the user never sees.
     */
    fun showsContent(hasGate: Boolean): Boolean = ready && !(hasGate && session == SessionState.Unknown)
}

/**
 * The shell's runtime state. The visible tabs are decided once per process, after the remote
 * config arrives or [CONFIG_WAIT_MS] passes, so tabs never disappear while the app is in use; a
 * config that arrives later is cached and applies on the next launch.
 */
class ShellViewModel(
    private val definition: DefinitionSpec,
    private val brand: BrandConfig,
    private val appConfig: AppConfigRepository,
    preferences: UserPreferencesRepository,
    session: SessionRepository,
    private val users: UserRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    /** A deep link waiting to be opened (held until sign-in); survives process death. */
    val pendingDeepLink: StateFlow<String?> = savedState.getStateFlow(KEY_DEEP_LINK, null)

    fun onDeepLink(link: String) {
        savedState[KEY_DEEP_LINK] = link
    }

    fun onDeepLinkConsumed() {
        savedState[KEY_DEEP_LINK] = null
    }

    private val tabs = MutableStateFlow<List<String>?>(null)

    val state: StateFlow<ShellState> = combine(tabs, preferences.theme, appConfig.config, session.state) {
            tabs, theme, config, sessionState ->
        ShellState(
            ready = tabs != null,
            visibleTabRoutes = tabs.orEmpty(),
            theme = theme ?: config.defaultTheme,
            session = sessionState,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ShellState())

    init {
        definition.backendProblem(brand.hasBackend)?.let(::error)
        viewModelScope.launch {
            // Without a backend the config is the app's build-time defaults: nothing to wait for.
            if (brand.hasBackend) {
                val refresh = launch { appConfig.refresh() }
                withTimeoutOrNull(CONFIG_WAIT_MS) { refresh.join() }
            }
            tabs.value = definition.visibleTabRoutes(appConfig.current().features, brand.runtimeDefaults.enabledFeatures)
        }
        viewModelScope.launch {
            if (session.state.first { it != SessionState.Unknown } == SessionState.SignedIn) users.refresh()
        }
    }
}
