package com.whitelabel.core.ios

import com.whitelabel.core.presentation.UiText
import com.whitelabel.core.presentation.asStringAsync
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.ResourceEnvironment
import org.jetbrains.compose.resources.getSystemResourceEnvironment

/**
 * Shared messages for Swift: [resolve] gives a [UiText]'s text in the language the app launched in.
 *
 * iOS applies a language picked in the app at the next launch, and the app's own (Swift) strings
 * stay in the launch language until then. Compose resources read the preferred languages on every
 * call, which change as soon as the pick is stored, so the environment is pinned at launch: never a
 * screen half in one language and half in another.
 */
@OptIn(ExperimentalResourceApi::class)
object Texts {
    private var launchEnvironment: ResourceEnvironment? = null

    /** Pins the launch language (SharedGraph.start); later calls keep the first. */
    fun pinLaunchLanguage() {
        if (launchEnvironment == null) launchEnvironment = getSystemResourceEnvironment()
    }

    suspend fun resolve(text: UiText): String {
        pinLaunchLanguage()
        return text.asStringAsync(checkNotNull(launchEnvironment))
    }
}
