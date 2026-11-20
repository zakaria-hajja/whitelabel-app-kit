package com.whitelabel.core.designsystem.component

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.whitelabel.core.designsystem.theme.AppTheme
import com.whitelabel.core.designsystem.theme.ButtonStyle
import com.whitelabel.core.designsystem.theme.StylePack
import com.whitelabel.core.designsystem.theme.labelWeight
import com.whitelabel.core.designsystem.theme.outlinedBorderColor
import com.whitelabel.core.designsystem.theme.outlinedLabelColor
import com.whitelabel.core.designsystem.theme.shape

/** The pack's button shape: Material's pill unless it opts in ([com.whitelabel.core.designsystem.theme.ButtonStyle]). */
@Composable
private fun buttonShape(material: Shape): Shape = AppTheme.style.buttons.shape(MaterialTheme.shapes) ?: material

/** A button label in the pack's weight; Material's when the pack doesn't set one. */
@Composable
private fun ButtonLabel(text: String) {
    Text(text, fontWeight = AppTheme.style.buttons.labelWeight)
}

@Composable
fun AppPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = AppTheme.style.sizing.controlHeight),
        enabled = enabled && !loading,
        shape = buttonShape(ButtonDefaults.shape),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(end = AppTheme.spacing.sm)
                    .size(18.dp)
                    .testTag("AppButtonProgress"),
                strokeWidth = 2.dp,
            )
        }
        ButtonLabel(text)
    }
}

@Composable
fun AppSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val style = AppTheme.style.buttons
    val scheme = MaterialTheme.colorScheme
    val border = style.outlinedBorderColor(scheme)
    val label = style.outlinedLabelColor(scheme)
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = AppTheme.style.sizing.controlHeight),
        enabled = enabled,
        shape = buttonShape(ButtonDefaults.outlinedShape),
        colors = if (label == null) ButtonDefaults.outlinedButtonColors() else ButtonDefaults.outlinedButtonColors(contentColor = label),
        // Disabled keeps Material's border, so a disabled button still reads as disabled.
        border = if (border == null || !enabled) ButtonDefaults.outlinedButtonBorder(enabled) else BorderStroke(1.dp, border),
    ) {
        ButtonLabel(text)
    }
}

@Composable
fun AppTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = AppTheme.style.sizing.controlHeight),
        enabled = enabled,
        shape = buttonShape(ButtonDefaults.textShape),
    ) {
        ButtonLabel(text)
    }
}

@Preview(name = "Buttons")
@Preview(name = "Buttons dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun AppButtonsPreview() {
    AppTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            AppPrimaryButton(text = "Primary", onClick = {})
            AppPrimaryButton(text = "Loading", onClick = {}, loading = true)
            AppPrimaryButton(text = "Disabled", onClick = {}, enabled = false)
            AppSecondaryButton(text = "Secondary", onClick = {})
            Row { AppTextButton(text = "Text", onClick = {}) }
        }
    }
}

@Preview(name = "Squared buttons")
@Preview(name = "Squared buttons dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun AppButtonsSquaredPreview() {
    AppTheme(stylePack = StylePack.Gallery.copy(buttons = ButtonStyle.Squared)) {
        Column(modifier = Modifier.padding(16.dp)) {
            AppPrimaryButton(text = "Primary", onClick = {})
            AppPrimaryButton(text = "Disabled", onClick = {}, enabled = false)
            AppSecondaryButton(text = "Secondary", onClick = {})
            AppSecondaryButton(text = "Secondary disabled", onClick = {}, enabled = false)
            Row { AppTextButton(text = "Text", onClick = {}) }
        }
    }
}
