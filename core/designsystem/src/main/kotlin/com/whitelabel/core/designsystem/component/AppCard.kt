package com.whitelabel.core.designsystem.component

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.whitelabel.core.designsystem.theme.AppTheme

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val inner: @Composable ColumnScope.() -> Unit = {
        Column(modifier = Modifier.padding(AppTheme.spacing.md), content = content)
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, content = inner)
    } else {
        Card(modifier = modifier, content = inner)
    }
}

@Preview(name = "Cards")
@Preview(name = "Cards dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun AppCardPreview() {
    AppTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            AppCard { Text("Static card") }
            AppCard(onClick = {}) { Text("Clickable card") }
        }
    }
}
