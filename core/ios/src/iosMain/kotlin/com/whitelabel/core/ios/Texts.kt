package com.whitelabel.core.ios

import com.whitelabel.core.presentation.UiText
import com.whitelabel.core.presentation.asStringAsync

/** Shared messages for Swift: [resolve] gives a [UiText]'s text in the device language. */
object Texts {
    suspend fun resolve(text: UiText): String = text.asStringAsync()
}
