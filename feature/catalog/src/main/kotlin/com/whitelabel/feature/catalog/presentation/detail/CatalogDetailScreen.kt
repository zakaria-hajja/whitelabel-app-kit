package com.whitelabel.feature.catalog.presentation.detail

import com.whitelabel.core.presentation.asString
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whitelabel.core.designsystem.component.AppLoadingIndicator
import com.whitelabel.core.designsystem.component.AppScaffold
import com.whitelabel.core.designsystem.component.EmptyState
import com.whitelabel.core.designsystem.component.ErrorState
import com.whitelabel.core.designsystem.theme.AppTheme
import com.whitelabel.feature.catalog.R
import com.whitelabel.feature.catalog.domain.CatalogItem
import com.whitelabel.feature.catalog.presentation.CatalogImage

@Composable
internal fun CatalogDetailRoot(onBack: () -> Unit, viewModel: CatalogDetailViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CatalogDetailScreen(state = state, onAction = viewModel::onAction, onBack = onBack)
}

@Composable
internal fun CatalogDetailScreen(state: CatalogDetailState, onAction: (CatalogDetailAction) -> Unit, onBack: () -> Unit) {
    AppScaffold(title = state.item?.title ?: stringResource(R.string.catalog_title), onNavigateBack = onBack) { innerPadding ->
        val modifier = Modifier.fillMaxSize().padding(innerPadding)
        val item = state.item
        when {
            item != null -> ItemDetail(item, modifier)
            state.isLoading -> Box(modifier, contentAlignment = Alignment.Center) { AppLoadingIndicator() }
            state.notFound -> EmptyState(
                title = stringResource(R.string.catalog_title),
                message = stringResource(R.string.catalog_item_not_found),
                modifier = modifier,
            )
            else -> ErrorState(
                message = state.error?.asString().orEmpty(),
                onRetry = { onAction(CatalogDetailAction.OnRetry) },
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun ItemDetail(item: CatalogItem, modifier: Modifier) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(AppTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.md),
    ) {
        CatalogImage(item.imageUrl, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)))
        Text(item.title, style = MaterialTheme.typography.headlineSmall)
        item.category?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
        item.description?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
    }
}

@Preview
@Composable
private fun CatalogDetailScreenPreview() {
    AppTheme {
        CatalogDetailScreen(
            state = CatalogDetailState(item = CatalogItem("i1", "Item 1", "A description of item 1.", "Music", null), isLoading = false),
            onAction = {},
            onBack = {},
        )
    }
}

@Preview
@Composable
private fun CatalogDetailNotFoundPreview() {
    AppTheme(darkTheme = true) {
        CatalogDetailScreen(state = CatalogDetailState(isLoading = false, notFound = true), onAction = {}, onBack = {})
    }
}
