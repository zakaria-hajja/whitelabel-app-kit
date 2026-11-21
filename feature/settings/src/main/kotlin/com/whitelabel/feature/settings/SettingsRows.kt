package com.whitelabel.feature.settings

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.whitelabel.core.designsystem.component.AppSheetDragHandle
import com.whitelabel.core.designsystem.component.AppTextButton
import com.whitelabel.core.designsystem.component.appRadioButtonColors
import com.whitelabel.core.designsystem.theme.AppTheme
import com.whitelabel.core.designsystem.theme.StylePack
import com.whitelabel.core.designsystem.theme.ValueRowLayout
import com.whitelabel.core.designsystem.theme.showsChevron

/**
 * A setting and its current value ("Theme · System default"), on one line, with the value under the
 * label, or at the trailing edge with a chevron, as the style pack says; tapping it usually opens a [SettingsChoiceSheet]. Use it in an app's
 * own `SettingsSection` so its rows match the shared ones. A null [onClick] makes it read-only: not a
 * button, so it neither ripples nor announces itself as one.
 */
@Composable
fun SettingsValueRow(label: String, value: String, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val layout = AppTheme.style.valueRows
    val chevron = layout.showsChevron(clickable = onClick != null)
    val row = modifier
        .fillMaxWidth()
        // Inline and disclosure keep the 48 dp rows (as the toggle rows); stacked rows hold two lines.
        .heightIn(min = if (layout == ValueRowLayout.Stacked) AppTheme.style.sizing.listRowMinHeight else 48.dp)
        .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
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
        // The value hugs the trailing edge (at most half the row, so a long one wraps beside the label),
        // then a chevron when the row opens something, as iOS draws it.
        ValueRowLayout.Disclosure -> BoxWithConstraints(modifier = row, contentAlignment = Alignment.CenterStart) {
            val valueMax = maxWidth / 2
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.padding(start = AppTheme.spacing.md).widthIn(max = valueMax),
                )
                if (chevron) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null, // The row is the button; its label and value say what it is.
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = AppTheme.spacing.xs).size(18.dp).testTag(SETTINGS_ROW_CHEVRON),
                    )
                }
            }
        }
    }
}

/** Test tag of a disclosure row's chevron. */
internal const val SETTINGS_ROW_CHEVRON = "SettingsValueRow:Chevron"

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
    ModalBottomSheet(onDismissRequest = onDismiss, dragHandle = { AppSheetDragHandle() }) {
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
                        RadioButton(selected = choice == selected, onClick = null, colors = appRadioButtonColors())
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

@Composable
private fun DisclosureRowsSample() {
    AppTheme(stylePack = StylePack.Gallery.copy(valueRows = ValueRowLayout.Disclosure)) {
        Surface {
            Column {
                SettingsValueRow("Remind me at", "20:00", onClick = {})
                SettingsValueRow("Before next period", "Two days before, at 20:00, and again in the morning", onClick = {})
                SettingsValueRow("Read-only", "28 days", onClick = null)
            }
        }
    }
}

@Preview(name = "Disclosure rows")
@Preview(name = "Disclosure rows dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun DisclosureRowsPreview() = DisclosureRowsSample()

@Preview(name = "Disclosure rows 200%", fontScale = 2f)
@Composable
private fun DisclosureRowsLargePreview() = DisclosureRowsSample()
