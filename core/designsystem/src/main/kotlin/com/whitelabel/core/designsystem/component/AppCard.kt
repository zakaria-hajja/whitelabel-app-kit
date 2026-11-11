package com.whitelabel.core.designsystem.component

import com.whitelabel.core.designsystem.theme.SurfaceStyle
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.BorderStroke
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
    // Hairline packs separate cards with an outline instead of a shadow.
    if (AppTheme.style.surfaces == SurfaceStyle.Hairline) {
        val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        val tagged = modifier.testTag("AppCard:Hairline")
        if (onClick != null) {
            OutlinedCard(onClick = onClick, modifier = tagged, border = border, content = inner)
        } else {
            OutlinedCard(modifier = tagged, border = border, content = inner)
        }
    } else {
        val tagged = modifier.testTag("AppCard:Shadow")
        if (onClick != null) {
            Card(onClick = onClick, modifier = tagged, content = inner)
        } else {
            Card(modifier = tagged, content = inner)
        }
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
