package com.whitelabel.core.designsystem.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.em
import com.whitelabel.core.designsystem.theme.AppTheme
import com.whitelabel.core.designsystem.theme.SectionHeaderStyle

/**
 * A section's title, as the app's style pack draws them: an accent title (Material) or a small
 * uppercase caption (Gallery). Use it for app screens' sections so they match the shared ones.
 */
@Composable
fun AppSectionHeader(text: String, modifier: Modifier = Modifier) {
    val padded = modifier
        .padding(horizontal = AppTheme.style.sizing.screenGutter)
        .padding(top = AppTheme.spacing.lg, bottom = AppTheme.spacing.sm)
        .semantics { heading() }
    when (AppTheme.style.sectionHeader) {
        SectionHeaderStyle.Accent -> Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = padded,
        )
        SectionHeaderStyle.Caption -> Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.12.em),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = padded,
        )
    }
}
