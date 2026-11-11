package com.whitelabel.feature.catalog.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whitelabel.core.analytics.Analytics
import com.whitelabel.core.analytics.AnalyticsEvent
import com.whitelabel.core.analytics.AnalyticsEvents
import com.whitelabel.core.common.ApiErrorCode
import com.whitelabel.core.common.DataError
import com.whitelabel.core.common.onFailure
import com.whitelabel.core.common.onSuccess
import com.whitelabel.core.ui.UiText
import com.whitelabel.core.ui.toUiText
import com.whitelabel.feature.catalog.domain.CatalogDataSource
import com.whitelabel.feature.catalog.domain.CatalogItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class CatalogDetailState(
    val item: CatalogItem? = null,
    val isLoading: Boolean = true,
    /** The id doesn't exist (or belongs to another tenant): no retry. */
    val notFound: Boolean = false,
    val error: UiText? = null,
)

internal sealed interface CatalogDetailAction {
    data object OnRetry : CatalogDetailAction
}

/** One item, opened from the list or from `<scheme>://catalog/{id}`; the id is the route's `id`. */
internal class CatalogDetailViewModel(
    private val source: CatalogDataSource,
    private val analytics: Analytics,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val id: String = savedState.get<String>("id").orEmpty()
    private var tracked = false

    private val _state = MutableStateFlow(CatalogDetailState())
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun onAction(action: CatalogDetailAction) {
        when (action) {
            CatalogDetailAction.OnRetry -> load()
        }
    }

    private fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            source.item(id)
                .onSuccess { item ->
                    _state.update { CatalogDetailState(item = item, isLoading = false) }
                    if (!tracked) {
                        tracked = true
                        analytics.track(AnalyticsEvent(AnalyticsEvents.VIEW_ITEM, mapOf("item_id" to item.id)))
                    }
                }
                .onFailure { error ->
                    _state.update {
                        if (error is DataError.Api && error.code == ApiErrorCode.NOT_FOUND) {
                            it.copy(isLoading = false, notFound = true)
                        } else {
                            it.copy(isLoading = false, error = error.toUiText())
                        }
                    }
                }
        }
    }
}
