package com.whitelabel.core.designsystem.catalog

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.whitelabel.core.designsystem.component.AppCard
import com.whitelabel.core.designsystem.component.AppLoadingIndicator
import com.whitelabel.core.designsystem.component.AppPrimaryButton
import com.whitelabel.core.designsystem.component.AppSecondaryButton
import com.whitelabel.core.designsystem.component.AppTextButton
import com.whitelabel.core.designsystem.component.AppTextField
import com.whitelabel.core.designsystem.component.EmptyState
import com.whitelabel.core.designsystem.component.ErrorState
import com.whitelabel.core.designsystem.theme.AppTheme

/** Every design-system building block on one scrollable page, for checking a brand at a glance. */
@Composable
fun DesignSystemCatalog(modifier: Modifier = Modifier) {
    var text by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(AppTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.md),
    ) {
        Section("Typography") {
            Text("Headline", style = MaterialTheme.typography.headlineMedium)
            Text("Title", style = MaterialTheme.typography.titleMedium)
            Text("Body", style = MaterialTheme.typography.bodyLarge)
            Text("Label", style = MaterialTheme.typography.labelLarge)
        }
        Section("Buttons") {
            AppPrimaryButton(text = "Primary", onClick = {})
            AppPrimaryButton(text = "Loading", onClick = {}, loading = true)
            AppSecondaryButton(text = "Secondary", onClick = {})
            AppTextButton(text = "Text", onClick = {})
            AppPrimaryButton(text = "Disabled", onClick = {}, enabled = false)
        }
        Section("Text fields") {
            AppTextField(value = text, onValueChange = { text = it }, label = "Label")
            AppTextField(value = password, onValueChange = { password = it }, label = "Password", isPassword = true)
            AppTextField(value = "", onValueChange = {}, label = "With error", error = "Something is wrong")
        }
        Section("Cards") {
            AppCard { Text("Card content") }
            AppCard(onClick = {}) { Text("Clickable card") }
        }
        Section("Loading") { AppLoadingIndicator() }
        Section("Error") { ErrorState(message = "Something went wrong.", onRetry = {}) }
        Section("Empty") {
            EmptyState(title = "Nothing here", message = "Items will show up here.", actionLabel = "Add", onAction = {})
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Preview(name = "Catalog", heightDp = 1400)
@Preview(name = "Catalog dark", heightDp = 1400, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun DesignSystemCatalogPreview() {
    AppTheme {
        Surface(modifier = Modifier.fillMaxSize()) { DesignSystemCatalog() }
    }
}
