package com.whitelabel.feature.catalog.presentation.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whitelabel.core.analytics.Analytics
import com.whitelabel.core.analytics.AnalyticsEvent
import com.whitelabel.core.analytics.AnalyticsEvents
import com.whitelabel.core.common.ApiErrorCode
import com.whitelabel.core.common.DataError
import com.whitelabel.core.common.Result
import com.whitelabel.core.presentation.UiText
import com.whitelabel.core.presentation.toUiText
import com.whitelabel.feature.catalog.CatalogOptions
import com.whitelabel.feature.catalog.CatalogSort
import com.whitelabel.feature.catalog.data.MAX_QUERY_LENGTH
import com.whitelabel.feature.catalog.domain.CatalogDataSource
import com.whitelabel.feature.catalog.domain.CatalogItem
import com.whitelabel.feature.catalog.domain.CatalogQuery
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How long typing has to pause before the search goes out. */
const val SEARCH_DEBOUNCE_MS = 300L

data class CatalogListState(
    /** The search field as typed. */
    val text: String = "",
    val category: String? = null,
    val sort: CatalogSort = CatalogSort.NEWEST,
    val items: List<CatalogItem> = emptyList(),
    val page: Int = 0,
    val isLoadingFirst: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val endReached: Boolean = false,
    /** The first page failed and there is nothing to show: full-screen error with retry. */
    val firstPageError: UiText? = null,
    /** A later page failed: the list stays, with a retry at its end. */
    val moreError: UiText? = null,
    /** The backend has the catalog switched off for this tenant. */
    val unavailable: Boolean = false,
    /** A one-off message (a refresh that failed over a list already shown). */
    val message: UiText? = null,
)

sealed interface CatalogListAction {
    data class OnQueryChange(val text: String) : CatalogListAction
    data class OnCategorySelect(val category: String?) : CatalogListAction
    data class OnSortSelect(val sort: CatalogSort) : CatalogListAction
    data object OnNearEnd : CatalogListAction
    data object OnRetry : CatalogListAction
    data object OnRefresh : CatalogListAction
    data object OnMessageShown : CatalogListAction
}

/**
 * The paginated, searchable list. Typing is debounced; every new query cancels the load in flight,
 * so an older query's results never replace a newer one's.
 */
@OptIn(FlowPreview::class)
class CatalogListViewModel(
    private val options: CatalogOptions,
    private val source: CatalogDataSource,
    private val analytics: Analytics,
) : ViewModel() {

    private val _state = MutableStateFlow(CatalogListState(sort = options.defaultSort))
    val state = _state.asStateFlow()

    private val text = MutableStateFlow("")
    private val category = MutableStateFlow<String?>(null)
    private val sort = MutableStateFlow(options.defaultSort)
    private val reloads = MutableStateFlow(0)

    private var currentQuery = CatalogQuery(sort = options.defaultSort)
    private var moreJob: Job? = null

    init {
        viewModelScope.launch {
            val settledText = text
                .debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS }
                .distinctUntilChanged { old, new -> old.trim() == new.trim() }
                .onEach { if (it.isNotBlank()) analytics.track(AnalyticsEvent(AnalyticsEvents.SEARCH, mapOf("length" to it.trim().length.toString()))) }
            combine(settledText, category, sort, reloads) { text, category, sort, _ -> CatalogQuery(text, category, sort) }
                .collectLatest { query -> loadFirst(query) }
        }
    }

    fun onAction(action: CatalogListAction) {
        when (action) {
            is CatalogListAction.OnQueryChange -> {
                val capped = action.text.take(MAX_QUERY_LENGTH)
                _state.update { it.copy(text = capped) }
                text.value = capped
            }
            is CatalogListAction.OnCategorySelect -> {
                _state.update { it.copy(category = action.category) }
                category.value = action.category
            }
            is CatalogListAction.OnSortSelect -> {
                _state.update { it.copy(sort = action.sort) }
                sort.value = action.sort
            }
            CatalogListAction.OnNearEnd -> loadMore()
            CatalogListAction.OnRetry -> retry()
            CatalogListAction.OnRefresh -> {
                _state.update { it.copy(isRefreshing = true) }
                reloads.value++
            }
            CatalogListAction.OnMessageShown -> _state.update { it.copy(message = null) }
        }
    }

    private suspend fun loadFirst(query: CatalogQuery) {
        moreJob?.cancel()
        // Only a refresh of the same query keeps the list on screen; a new query never shows the old one's items.
        val keepList = _state.value.isRefreshing && query == currentQuery
        currentQuery = query
        _state.update {
            it.copy(
                isLoadingFirst = !keepList,
                isRefreshing = keepList,
                items = if (keepList) it.items else emptyList(),
                isLoadingMore = false,
                endReached = false,
                firstPageError = null,
                moreError = null,
                unavailable = false,
            )
        }
        val result = source.page(query, page = 1, pageSize = options.pageSize)
        _state.update { state ->
            val done = state.copy(isLoadingFirst = false, isRefreshing = false)
            when (result) {
                is Result.Success -> done.copy(
                    items = result.data.items,
                    page = 1,
                    endReached = result.data.isLast(options.pageSize),
                )
                is Result.Error -> when {
                    result.error.isFeatureDisabled() -> done.copy(items = emptyList(), unavailable = true)
                    done.items.isEmpty() -> done.copy(firstPageError = result.error.toUiText())
                    else -> done.copy(message = result.error.toUiText())
                }
            }
        }
    }

    private fun loadMore() {
        val current = _state.value
        if (current.isLoadingFirst || current.isRefreshing || current.isLoadingMore || current.endReached ||
            current.moreError != null || current.items.isEmpty()
        ) {
            return
        }
        _state.update { it.copy(isLoadingMore = true) }
        val query = currentQuery
        moreJob = viewModelScope.launch {
            val next = current.page + 1
            when (val result = source.page(query, page = next, pageSize = options.pageSize)) {
                is Result.Success -> _state.update {
                    it.copy(
                        // Offset paging repeats an item when one is added meanwhile; a repeated key would crash the list.
                        items = (it.items + result.data.items).distinctBy { item -> item.id },
                        page = next,
                        endReached = result.data.isLast(options.pageSize),
                        isLoadingMore = false,
                    )
                }
                is Result.Error -> _state.update { it.copy(isLoadingMore = false, moreError = result.error.toUiText()) }
            }
        }
    }

    private fun retry() {
        val current = _state.value
        when {
            current.moreError != null -> {
                _state.update { it.copy(moreError = null) }
                loadMore()
            }
            current.firstPageError != null -> reloads.value++
        }
    }

    private fun com.whitelabel.core.network.Page<CatalogItem>.isLast(pageSize: Int) = items.size < pageSize || page >= totalPages

    private fun DataError.isFeatureDisabled() = this is DataError.Api && code == ApiErrorCode.FEATURE_DISABLED
}
