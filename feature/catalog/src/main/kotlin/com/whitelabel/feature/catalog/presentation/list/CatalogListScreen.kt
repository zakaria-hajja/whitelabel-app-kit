package com.whitelabel.feature.catalog.presentation.list

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whitelabel.core.designsystem.component.AppCard
import com.whitelabel.core.designsystem.component.AppLoadingIndicator
import com.whitelabel.core.designsystem.component.AppScaffold
import com.whitelabel.core.designsystem.component.AppTextButton
import com.whitelabel.core.designsystem.component.AppTextField
import com.whitelabel.core.designsystem.component.EmptyState
import com.whitelabel.core.designsystem.component.ErrorState
import com.whitelabel.core.designsystem.theme.AppTheme
import com.whitelabel.feature.catalog.CatalogOptions
import com.whitelabel.feature.catalog.CatalogSort
import com.whitelabel.feature.catalog.R
import com.whitelabel.feature.catalog.domain.CatalogItem
import com.whitelabel.feature.catalog.presentation.CatalogImage
import kotlinx.coroutines.flow.first

/** Ask for the next page when the last visible item is this close to the end. */
private const val PREFETCH_DISTANCE = 5

@Composable
internal fun CatalogListRoot(options: CatalogOptions, onItemClick: (String) -> Unit) {
    val viewModel = koinViewModel<CatalogListViewModel> { parametersOf(options) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    CatalogListScreen(state = state, options = options, onAction = viewModel::onAction, onItemClick = onItemClick)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CatalogListScreen(
    state: CatalogListState,
    options: CatalogOptions,
    onAction: (CatalogListAction) -> Unit,
    onItemClick: (String) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val message = state.message?.asString()
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onAction(CatalogListAction.OnMessageShown)
        }
    }
    AppScaffold(
        title = stringResource(R.string.catalog_title),
        snackbarHostState = snackbarHostState,
        actions = { SortMenu(state.sort) { onAction(CatalogListAction.OnSortSelect(it)) } },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            AppTextField(
                value = state.text,
                onValueChange = { onAction(CatalogListAction.OnQueryChange(it)) },
                label = stringResource(R.string.catalog_search),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppTheme.spacing.md),
            )
            if (options.categories.isNotEmpty()) CategoryChips(options.categories, state.category, onAction)
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { onAction(CatalogListAction.OnRefresh) },
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.unavailable -> EmptyState(
                        title = stringResource(R.string.catalog_unavailable_title),
                        message = stringResource(R.string.catalog_unavailable_message),
                        modifier = Modifier.fillMaxSize(),
                    )
                    state.isLoadingFirst -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { AppLoadingIndicator() }
                    state.firstPageError != null -> ErrorState(
                        message = state.firstPageError.asString(),
                        onRetry = { onAction(CatalogListAction.OnRetry) },
                        modifier = Modifier.fillMaxSize(),
                    )
                    state.items.isEmpty() -> EmptyState(
                        title = stringResource(R.string.catalog_empty_title),
                        message = stringResource(R.string.catalog_empty_message),
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> ItemList(state, onAction, onItemClick)
                }
            }
        }
    }
}

@Composable
private fun ItemList(state: CatalogListState, onAction: (CatalogListAction) -> Unit, onItemClick: (String) -> Unit) {
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            // Nothing is laid out yet on the first frame: that is not "near the end".
            info.totalItemsCount > 0 &&
                (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - PREFETCH_DISTANCE
        }
    }
    // Asks once per page shown: when a page fits on screen the end stays visible, so waiting for
    // "near the end" to turn true again would stall the list.
    LaunchedEffect(listState, state.items.size) {
        snapshotFlow { nearEnd }.first { it }
        onAction(CatalogListAction.OnNearEnd)
    }
    LazyColumn(
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(AppTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(state.items, key = { it.id }) { item -> ItemRow(item) { onItemClick(item.id) } }
        item(key = "footer") {
            when {
                state.isLoadingMore -> Box(Modifier.fillMaxWidth().padding(AppTheme.spacing.md), contentAlignment = Alignment.Center) {
                    AppLoadingIndicator()
                }
                state.moreError != null -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.moreError.asString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    AppTextButton(text = stringResource(R.string.catalog_retry), onClick = { onAction(CatalogListAction.OnRetry) })
                }
            }
        }
    }
}

@Composable
private fun ItemRow(item: CatalogItem, onClick: () -> Unit) {
    AppCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.md)) {
            CatalogImage(item.imageUrl, Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                item.category?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryChips(categories: List<String>, selected: String?, onAction: (CatalogListAction) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = AppTheme.spacing.md, vertical = AppTheme.spacing.sm),
    ) {
        FilterChip(selected = selected == null, onClick = { onAction(CatalogListAction.OnCategorySelect(null)) }, label = { Text(stringResource(R.string.catalog_all)) })
        categories.forEach { category ->
            FilterChip(
                selected = selected == category,
                onClick = { onAction(CatalogListAction.OnCategorySelect(if (selected == category) null else category)) },
                label = { Text(category) },
            )
        }
    }
}

@Composable
private fun SortMenu(sort: CatalogSort, onSelect: (CatalogSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AppTextButton(text = stringResource(R.string.catalog_sort), onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            CatalogSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.label()), style = fontWeightIfSelected(option == sort)) },
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun fontWeightIfSelected(selected: Boolean) =
    if (selected) MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) else MaterialTheme.typography.bodyLarge

private fun CatalogSort.label() = when (this) {
    CatalogSort.NEWEST -> R.string.catalog_sort_newest
    CatalogSort.OLDEST -> R.string.catalog_sort_oldest
    CatalogSort.TITLE_ASC -> R.string.catalog_sort_title_asc
    CatalogSort.TITLE_DESC -> R.string.catalog_sort_title_desc
}

@Preview
@Composable
private fun CatalogListScreenPreview() {
    AppTheme {
        CatalogListScreen(
            state = CatalogListState(items = List(4) { CatalogItem("i$it", "Item $it", null, "Music", null) }, page = 1),
            options = CatalogOptions(categories = listOf("Books", "Music")),
            onAction = {},
            onItemClick = {},
        )
    }
}
