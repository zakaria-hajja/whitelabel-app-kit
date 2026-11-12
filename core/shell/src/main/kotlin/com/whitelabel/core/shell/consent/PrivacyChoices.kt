package com.whitelabel.core.shell.consent

import com.whitelabel.core.consent.Consent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What the privacy choices row needs, without any UI: it is [visible] while UMP says privacy options
 * are required, and [open] shows them. With no [consent] (an app without Firebase) it is never visible.
 */
internal class PrivacyChoices(private val consent: Consent?) {
    val visible: StateFlow<Boolean> = consent?.privacyOptionsRequired ?: MutableStateFlow(false)

    fun open() {
        consent?.showPrivacyOptions()
    }
}
