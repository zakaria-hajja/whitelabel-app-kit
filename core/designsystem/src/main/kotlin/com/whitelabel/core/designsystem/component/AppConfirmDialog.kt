package com.whitelabel.core.designsystem.component

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.whitelabel.core.designsystem.theme.AppTheme

@Composable
fun AppConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { AppTextButton(text = confirmLabel, onClick = onConfirm) },
        dismissButton = { AppTextButton(text = dismissLabel, onClick = onDismiss) },
    )
}

@Preview(name = "Confirm dialog")
@Preview(name = "Confirm dialog dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun AppConfirmDialogPreview() {
    AppTheme {
        AppConfirmDialog(
            title = "Delete item?",
            text = "This cannot be undone.",
            confirmLabel = "Delete",
            dismissLabel = "Cancel",
            onConfirm = {},
            onDismiss = {},
        )
    }
}
