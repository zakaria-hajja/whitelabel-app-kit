package com.whitelabel.feature.home.presentation

import com.whitelabel.core.presentation.asString
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whitelabel.core.designsystem.component.AppCard
import com.whitelabel.core.designsystem.component.AppScaffold
import com.whitelabel.core.designsystem.theme.AppTheme
import com.whitelabel.feature.home.HomeOptions
import com.whitelabel.feature.home.HomeShortcut
import com.whitelabel.feature.home.R

@Composable
internal fun HomeRoot(
    options: HomeOptions,
    canOpenTab: (Any) -> Boolean,
    onOpenTab: (Any) -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(state = state, options = options, canOpenTab = canOpenTab, onOpenTab = onOpenTab, onAction = viewModel::onAction)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
    state: HomeState,
    options: HomeOptions,
    canOpenTab: (Any) -> Boolean,
    onOpenTab: (Any) -> Unit,
    onAction: (HomeAction) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val message = state.message?.asString()
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onAction(HomeAction.OnMessageShown)
        }
    }
    val context = LocalContext.current
    val appName = remember(context) { context.applicationInfo.loadLabel(context.packageManager).toString() }
    val shortcuts = options.shortcuts.filter { canOpenTab(it.tab) }
    AppScaffold(title = appName, snackbarHostState = snackbarHostState) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { onAction(HomeAction.OnRefresh) },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(AppTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.md),
            ) {
                Text(state.greeting.asString(), style = MaterialTheme.typography.headlineMedium)
                if (shortcuts.isNotEmpty()) {
                    Text(stringResource(R.string.home_shortcuts), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    shortcuts.forEach { shortcut -> ShortcutCard(shortcut) { onOpenTab(shortcut.tab) } }
                }
            }
        }
    }
}

@Composable
private fun ShortcutCard(shortcut: HomeShortcut, onClick: () -> Unit) {
    AppCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.md),
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Icon(shortcut.icon, contentDescription = null)
            Text(stringResource(shortcut.label), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Preview
@Composable
private fun HomeScreenPreview() {
    AppTheme {
        HomeScreen(
            state = HomeState(),
            options = HomeOptions(listOf(HomeShortcut(R.string.home_tab, Icons.Default.Person, Unit))),
            canOpenTab = { true },
            onOpenTab = {},
            onAction = {},
        )
    }
}
