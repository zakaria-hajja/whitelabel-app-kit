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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
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
    // Premise verified: OutlinedTextField caches its Password semantics from first composition
    // and an extra semantics modifier cannot remove them, so the field is keyed on [hidden] to
    // keep a revealed password from being announced as one. Recreating it drops focus and the
    // caret, so both live outside the key and are restored after a toggle.
    val focusRequester = remember { FocusRequester() }
    var hasFocus by remember { mutableStateOf(false) }
    var restoreFocus by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (field.text != value) field = field.copy(text = value, selection = TextRange(value.length))
    key(hidden) {
        OutlinedTextField(
            value = field,
            onValueChange = {
                field = it
                if (it.text != value) onValueChange(it.text)
            },
            modifier = modifier
                .focusRequester(focusRequester)
                .onFocusChanged { if (restoreFocus.not()) hasFocus = it.isFocused },
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
                    IconButton(
                        onClick = {
                            restoreFocus = hasFocus
                            passwordVisible = !passwordVisible
                        },
                    ) {
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
    LaunchedEffect(hidden) {
        if (restoreFocus) {
            focusRequester.requestFocus()
            restoreFocus = false
        }
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
