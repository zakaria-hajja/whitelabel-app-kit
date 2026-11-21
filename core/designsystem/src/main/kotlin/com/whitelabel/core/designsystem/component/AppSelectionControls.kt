package com.whitelabel.core.designsystem.component

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonColors
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.whitelabel.core.designsystem.theme.AppTheme
import com.whitelabel.core.designsystem.theme.SelectionControlStyle
import com.whitelabel.core.designsystem.theme.StylePack
import com.whitelabel.core.designsystem.theme.radioUnselectedColor
import com.whitelabel.core.designsystem.theme.sheetHandleColor

/**
 * A bottom sheet's drag handle in the pack's color ([SelectionControlStyle]): pass it as
 * `ModalBottomSheet(dragHandle = { AppSheetDragHandle() })`. Material's handle unless the pack opts in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheetDragHandle(modifier: Modifier = Modifier) {
    val color = AppTheme.style.selectionControls.sheetHandleColor(MaterialTheme.colorScheme)
    if (color == null) BottomSheetDefaults.DragHandle(modifier) else BottomSheetDefaults.DragHandle(modifier, color = color)
}

/** Radio button colors in the pack's style ([SelectionControlStyle]); Material's unless the pack opts in. */
@Composable
fun appRadioButtonColors(): RadioButtonColors {
    val unselected = AppTheme.style.selectionControls.radioUnselectedColor(MaterialTheme.colorScheme)
    return if (unselected == null) RadioButtonDefaults.colors() else RadioButtonDefaults.colors(unselectedColor = unselected)
}

@Composable
private fun SelectionControlsSample(pack: StylePack) {
    AppTheme(stylePack = pack) {
        Surface {
            Column(Modifier.padding(16.dp)) {
                AppSheetDragHandle()
                Row {
                    RadioButton(selected = true, onClick = {}, colors = appRadioButtonColors())
                    RadioButton(selected = false, onClick = {}, colors = appRadioButtonColors())
                    Text(pack.selectionControls.name, Modifier.padding(start = 8.dp, top = 12.dp))
                }
            }
        }
    }
}

@Preview(name = "Material")
@Preview(name = "Material dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun SelectionControlsMaterialPreview() = SelectionControlsSample(StylePack.Gallery)

@Preview(name = "Outline")
@Preview(name = "Outline dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun SelectionControlsOutlinePreview() = SelectionControlsSample(StylePack.Gallery.copy(selectionControls = SelectionControlStyle.Outline))
