package com.whitelabel.core.designsystem.component

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.whitelabel.core.designsystem.theme.AppTheme

@Composable
fun AppLoadingIndicator(modifier: Modifier = Modifier) {
    CircularProgressIndicator(modifier = modifier)
}

@Composable
fun FullScreenLoading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("FullScreenLoading"),
        contentAlignment = Alignment.Center,
    ) {
        AppLoadingIndicator()
    }
}

@Preview(name = "Loading", heightDp = 200)
@Preview(name = "Loading dark", heightDp = 200, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun FullScreenLoadingPreview() {
    AppTheme { FullScreenLoading() }
}
