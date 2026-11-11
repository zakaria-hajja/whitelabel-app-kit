package com.whitelabel.core.presentation

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.ResourceEnvironment
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * Text for the UI that is either already a string or a shared string resource, resolved where it
 * is shown: [asString] in a composable, [asStringAsync] elsewhere (e.g. from Swift).
 */
sealed interface UiText {
    data class Dynamic(val value: String) : UiText

    data class Resource(val id: StringResource, val args: List<Any> = emptyList()) : UiText
}

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Dynamic -> value
    is UiText.Resource -> stringResource(id, *args.toTypedArray())
}

suspend fun UiText.asStringAsync(): String = when (this) {
    is UiText.Dynamic -> value
    is UiText.Resource -> getString(id, *args.toTypedArray())
}

/** [asStringAsync] in a fixed [environment] (iOS pins the language the app launched in). */
suspend fun UiText.asStringAsync(environment: ResourceEnvironment): String = when (this) {
    is UiText.Dynamic -> value
    is UiText.Resource -> getString(environment, id, *args.toTypedArray())
}
