package com.whitelabel.app.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whitelabel.core.branding.BrandConfig
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

data class BrandPreviewState(
    val brand: BrandConfig,
    /** `null` follows the system setting. */
    val darkTheme: Boolean? = null,
    val health: HealthUi = HealthUi.Idle,
)

sealed interface HealthUi {
    data object Idle : HealthUi
    data object Checking : HealthUi
    data class Up(val databaseUp: Boolean) : HealthUi
    data class Failed(val error: DataError) : HealthUi
}

sealed interface BrandPreviewAction {
    data object OnPingClick : BrandPreviewAction
    data class OnThemeSelect(val dark: Boolean?) : BrandPreviewAction
}

@HiltViewModel
class BrandPreviewViewModel @Inject constructor(
    brand: BrandConfig,
    private val health: HealthDataSource,
) : ViewModel() {

    private val _state = MutableStateFlow(BrandPreviewState(brand = brand))
    val state: StateFlow<BrandPreviewState> = _state.asStateFlow()

    fun onAction(action: BrandPreviewAction) {
        when (action) {
            BrandPreviewAction.OnPingClick -> ping()
            is BrandPreviewAction.OnThemeSelect -> _state.update { it.copy(darkTheme = action.dark) }
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
