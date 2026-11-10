package com.whitelabel.core.designsystem.component

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.whitelabel.core.designsystem.R
import com.whitelabel.core.designsystem.theme.AppTheme

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    isPassword: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
) {
    // Purely visual toggle; survives rotation, never leaves the field.
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val hidden = isPassword && !passwordVisible
    // Keyed on [hidden]: the text field caches its Password semantics from first composition,
    // so a plain recomposition would keep announcing a revealed password as a password.
    key(hidden) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            label = { Text(label) },
            isError = error != null,
            supportingText = error?.let { message -> { Text(message) } },
            singleLine = singleLine,
            keyboardOptions = if (hidden) {
                keyboardOptions.copy(keyboardType = KeyboardType.Password)
            } else {
                keyboardOptions
            },
            visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
            trailingIcon = if (isPassword) {
                {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            painter = painterResource(
                                if (passwordVisible) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
                            ),
                            contentDescription = stringResource(
                                if (passwordVisible) R.string.ds_hide_password else R.string.ds_show_password,
                            ),
                        )
                    }
                }
            } else {
                null
            },
        )
    }
}

@Preview(name = "Text fields")
@Preview(name = "Text fields dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun AppTextFieldPreview() {
    AppTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            AppTextField(value = "name@example.com", onValueChange = {}, label = "Email")
            AppTextField(value = "secret", onValueChange = {}, label = "Password", isPassword = true)
            AppTextField(value = "oops", onValueChange = {}, label = "Email", error = "Invalid email")
        }
    }
}
