package com.whitelabel.core.designsystem.component

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.whitelabel.core.designsystem.theme.AppTheme

private val MinButtonHeight = 48.dp

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
        modifier = modifier.heightIn(min = MinButtonHeight),
        enabled = enabled && !loading,
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
        Text(text)
    }
}

@Composable
fun AppSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = MinButtonHeight),
        enabled = enabled,
    ) {
        Text(text)
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
        modifier = modifier.heightIn(min = MinButtonHeight),
        enabled = enabled,
    ) {
        Text(text)
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
