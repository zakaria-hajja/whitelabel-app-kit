package com.whitelabel.feature.authentication.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.whitelabel.core.designsystem.component.AppScaffold
import com.whitelabel.core.designsystem.theme.AppTheme
import com.whitelabel.core.ui.UiText

/** The scrolling, keyboard-aware column every auth screen uses. */
@Composable
internal fun AuthForm(
    title: String,
    onNavigateBack: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    AppScaffold(title = title, onNavigateBack = onNavigateBack) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(AppTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.md),
            content = content,
        )
    }
}

/** A form-level error under the fields. */
@Composable
internal fun FormError(error: UiText?) {
    if (error != null) {
        Text(text = error.asString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
}
