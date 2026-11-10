package com.whitelabel.core.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** Text for the UI that is either already a string or a string resource resolved at display time. */
sealed interface UiText {
    data class Dynamic(val value: String) : UiText

    data class Resource(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    @Composable
    fun asString(): String = when (this) {
        is Dynamic -> value
        is Resource -> stringResource(id, *args.toTypedArray())
    }

    fun asString(context: Context): String = when (this) {
        is Dynamic -> value
        is Resource -> context.getString(id, *args.toTypedArray())
    }
}
