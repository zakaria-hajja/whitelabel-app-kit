package com.whitelabel.fixture.standalone

import com.whitelabel.core.shell.AppDefinition
import com.whitelabel.core.shell.appDefinition
import com.whitelabel.feature.settings.settings

/** The fixture uses only settings, which works without a backend (diagnostics is under Settings › About in debug). */
fun fixtureDefinition(): AppDefinition = appDefinition {
    settings()
}
