package com.whitelabel.core.designsystem.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.whitelabel.core.designsystem.theme.AppTheme
import com.whitelabel.core.designsystem.theme.SectionHeaderStyle
import com.whitelabel.core.designsystem.theme.sectionCaptionStyle

/**
 * A section's title, as the app's style pack draws them: an accent title (Material) or a small
 * uppercase caption (Gallery), whose text a pack can set with `StylePack.sectionCaptionText`. Use it for app screens' sections so they match the shared ones.
 */
@Composable
fun AppSectionHeader(text: String, modifier: Modifier = Modifier) {
    val style = AppTheme.style.sectionHeader
    // Accent keeps the spacing shared screens always had; Caption opens more air above, as designed.
    val top = if (style == SectionHeaderStyle.Caption) AppTheme.spacing.lg else AppTheme.spacing.sm
    val padded = modifier
        .padding(horizontal = AppTheme.style.sizing.screenGutter)
        .padding(top = top, bottom = AppTheme.spacing.sm)
        .semantics { heading() }
    when (style) {
        SectionHeaderStyle.Accent -> Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = padded,
        )
        SectionHeaderStyle.Caption -> Text(
            text = text.uppercase(),
            style = sectionCaptionStyle(AppTheme.style.sectionCaptionText, MaterialTheme.typography),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = padded,
        )
    }
}
