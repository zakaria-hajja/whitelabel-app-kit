package com.whitelabel.core.shell.consent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whitelabel.core.consent.Consent
import com.whitelabel.core.designsystem.theme.AppTheme
import com.whitelabel.core.shell.R
import org.koin.compose.getKoin

/**
 * "Privacy choices": opens Google's privacy options form. It shows only while UMP says they are
 * required, and draws nothing in an app without Firebase. Place it in the app's own `SettingsSection`.
 */
@Composable
fun PrivacyChoicesRow(modifier: Modifier = Modifier) {
    val koin = getKoin()
    val choices = remember(koin) { PrivacyChoices(koin.getOrNull<Consent>()) }
    val visible by choices.visible.collectAsStateWithLifecycle()
    if (!visible) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AppTheme.style.sizing.listRowMinHeight)
            .clickable(role = Role.Button, onClick = choices::open)
            .padding(horizontal = AppTheme.style.sizing.screenGutter),
    ) {
        Text(stringResource(R.string.privacy_choices), style = MaterialTheme.typography.bodyLarge)
    }
}
