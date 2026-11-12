package com.whitelabel.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.whitelabel.core.designsystem.component.AppTextButton
import com.whitelabel.core.designsystem.theme.AppTheme
import com.whitelabel.core.designsystem.theme.ValueRowLayout

/**
 * A setting and its current value ("Theme · System default"), on one line or with the value under the
 * label, as the style pack says; tapping it usually opens a [SettingsChoiceSheet]. Use it in an app's
 * own `SettingsSection` so its rows match the shared ones.
 */
@Composable
fun SettingsValueRow(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val layout = AppTheme.style.valueRows
    val row = modifier
        .fillMaxWidth()
        // Inline keeps the 48 dp rows (as the toggle rows); stacked rows hold two lines.
        .heightIn(min = if (layout == ValueRowLayout.Stacked) AppTheme.style.sizing.listRowMinHeight else 48.dp)
        .clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = AppTheme.style.sizing.screenGutter, vertical = AppTheme.spacing.sm)
    when (layout) {
        ValueRowLayout.Inline -> Row(verticalAlignment = Alignment.CenterVertically, modifier = row) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                // Weighted without filling: a short value hugs the end, a long one wraps instead of crushing the label.
                modifier = Modifier.weight(1f, fill = false).padding(start = AppTheme.spacing.md),
            )
        }
        // The label, then its value under it: long values wrap under the label instead of beside it.
        ValueRowLayout.Stacked -> Column(verticalArrangement = Arrangement.Center, modifier = row) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A row that opens something (a link, another screen): full width, at the screen gutter, one button. */
@Composable
fun SettingsLinkRow(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AppTheme.style.sizing.listRowMinHeight)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = AppTheme.style.sizing.screenGutter),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * A sheet to pick one of [choices]. Picking applies at once (there's no Save); **Done**, back, a tap
 * outside or a swipe down call [onDismiss].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SettingsChoiceSheet(
    title: String,
    choices: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = AppTheme.spacing.md)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = AppTheme.spacing.md, vertical = AppTheme.spacing.sm),
            )
            // Long lists (currencies, days) scroll; the title and Done stay in view.
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).selectableGroup()) {
                choices.forEach { choice ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = choice == selected, onClick = { onSelect(choice) }, role = Role.RadioButton)
                            .padding(horizontal = AppTheme.spacing.md),
                    ) {
                        RadioButton(selected = choice == selected, onClick = null)
                        Text(label(choice), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = AppTheme.spacing.md))
                    }
                }
            }
            AppTextButton(
                text = stringResource(R.string.settings_done),
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End).padding(horizontal = AppTheme.spacing.sm),
            )
        }
    }
}
