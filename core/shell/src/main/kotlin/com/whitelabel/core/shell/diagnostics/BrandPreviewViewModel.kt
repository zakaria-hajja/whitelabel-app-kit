package com.whitelabel.core.shell.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whitelabel.core.branding.BrandConfig
import com.whitelabel.core.shell.brand.UsingMockApi
import com.whitelabel.core.branding.hasBackend
import com.whitelabel.core.common.DataError
import com.whitelabel.core.common.onFailure
import com.whitelabel.core.common.onSuccess
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class BrandPreviewState(
    val brand: BrandConfig,
    val health: HealthUi = HealthUi.Idle,
    /** The in-memory mock backend answers instead of the network (`-Pwhitelabel.mockApi=true`). */
    val usingMockApi: Boolean = false,
) {
    /** The backend ping only makes sense when the app has a backend. */
    val showPing: Boolean get() = brand.hasBackend
}

internal sealed interface HealthUi {
    data object Idle : HealthUi
    data object Checking : HealthUi
    data class Up(val databaseUp: Boolean) : HealthUi
    data class Failed(val error: DataError) : HealthUi
}

internal sealed interface BrandPreviewAction {
    data object OnPingClick : BrandPreviewAction
}

@HiltViewModel
internal class BrandPreviewViewModel @Inject constructor(
    brand: BrandConfig,
    private val health: HealthDataSource,
    @UsingMockApi usingMockApi: Boolean = false,
) : ViewModel() {

    private val _state = MutableStateFlow(BrandPreviewState(brand = brand, usingMockApi = usingMockApi))
    val state: StateFlow<BrandPreviewState> = _state.asStateFlow()

    fun onAction(action: BrandPreviewAction) {
        when (action) {
            BrandPreviewAction.OnPingClick -> ping()
        }
    }

    private fun ping() {
        if (_state.value.health == HealthUi.Checking) return
        _state.update { it.copy(health = HealthUi.Checking) }
        viewModelScope.launch {
            health.check()
                .onSuccess { status -> _state.update { it.copy(health = HealthUi.Up(status.databaseUp)) } }
                .onFailure { error -> _state.update { it.copy(health = HealthUi.Failed(error)) } }
        }
    }
}
