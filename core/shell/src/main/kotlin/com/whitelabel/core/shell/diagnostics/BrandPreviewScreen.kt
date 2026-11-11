package com.whitelabel.core.shell.diagnostics

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whitelabel.core.shell.R
import com.whitelabel.core.branding.BrandConfig
import com.whitelabel.core.branding.Environment
import com.whitelabel.core.branding.RuntimeDefaults
import com.whitelabel.core.common.DataError
import com.whitelabel.core.designsystem.catalog.DesignSystemCatalog
import com.whitelabel.core.designsystem.component.AppCard
import com.whitelabel.core.designsystem.component.AppPrimaryButton
import com.whitelabel.core.designsystem.component.AppScaffold
import com.whitelabel.core.designsystem.theme.AppTheme

/**
 * Diagnostics: the app identity, a backend ping and the design-system catalog. Added to an app with
 * [diagnostics]; Phase 4 makes it debug-only under Settings › About.
 */
@Composable
internal fun BrandPreviewRoot(onBack: () -> Unit, viewModel: BrandPreviewViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BrandPreviewScreen(state = state, onAction = viewModel::onAction, onBack = onBack)
}

@Composable
internal fun BrandPreviewScreen(
    state: BrandPreviewState,
    onAction: (BrandPreviewAction) -> Unit,
    onBack: () -> Unit = {},
) {
    AppScaffold(title = state.brand.appName, onNavigateBack = onBack) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier.padding(AppTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm),
            ) {
                IdentityCard(brand = state.brand)
                if (state.showPing) {
                    HealthRow(health = state.health, onPing = { onAction(BrandPreviewAction.OnPingClick) })
                }
            }
            HorizontalDivider()
            DesignSystemCatalog(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun IdentityCard(brand: BrandConfig) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        LabeledValue(R.string.preview_label_app_name, brand.appName)
        LabeledValue(R.string.preview_label_slug, brand.tenant.orEmpty())
        LabeledValue(R.string.preview_label_environment, brand.environment.name)
        LabeledValue(R.string.preview_label_base_url, brand.apiBaseUrl ?: stringResource(R.string.preview_no_backend))
    }
}

@Composable
private fun LabeledValue(@StringRes label: Int, value: String) {
    Text(
        text = stringResource(R.string.preview_labeled_value, stringResource(label), value),
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun HealthRow(health: HealthUi, onPing: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.md),
    ) {
        AppPrimaryButton(
            text = stringResource(R.string.preview_ping),
            onClick = onPing,
            loading = health == HealthUi.Checking,
        )
        Text(
            text = stringResource(health.messageRes()),
            style = MaterialTheme.typography.bodyMedium,
            color = if (health is HealthUi.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@StringRes
private fun HealthUi.messageRes(): Int = when (this) {
    HealthUi.Idle -> R.string.preview_health_idle
    HealthUi.Checking -> R.string.preview_health_checking
    is HealthUi.Up -> if (databaseUp) R.string.preview_health_up else R.string.preview_health_db_down
    is HealthUi.Failed -> error.messageRes()
}

@StringRes
private fun DataError.messageRes(): Int = when (this) {
    DataError.Network.NO_INTERNET -> R.string.preview_error_no_internet
    DataError.Network.REQUEST_TIMEOUT -> R.string.preview_error_timeout
    DataError.Network.SERIALIZATION -> R.string.preview_error_unexpected_response
    is DataError.Api -> R.string.preview_error_server
    DataError.Network.UNKNOWN, is DataError.Local -> R.string.preview_error_unknown
}

private val PreviewBrand = BrandConfig(
    tenant = "preview",
    appName = "Preview",
    applicationId = "com.example.preview",
    versionName = "1.0.0",
    environment = Environment.DEVELOPMENT,
    apiBaseUrl = "https://api.example.com/api/v1/",
    deepLinkScheme = "preview",
    runtimeDefaults = RuntimeDefaults(
        supportEmail = null,
        privacyPolicyUrl = "https://example.com/privacy",
        termsUrl = "https://example.com/terms",
        enabledFeatures = emptySet(),
    ),
)

@Preview(showBackground = true)
@Composable
private fun BrandPreviewScreenIdlePreview() {
    AppTheme(darkTheme = false) {
        BrandPreviewScreen(state = BrandPreviewState(brand = PreviewBrand), onAction = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun BrandPreviewScreenFailedPreview() {
    AppTheme(darkTheme = true) {
        BrandPreviewScreen(
            state = BrandPreviewState(
                brand = PreviewBrand,
                health = HealthUi.Failed(DataError.Network.NO_INTERNET),
            ),
            onAction = {},
        )
    }
}
