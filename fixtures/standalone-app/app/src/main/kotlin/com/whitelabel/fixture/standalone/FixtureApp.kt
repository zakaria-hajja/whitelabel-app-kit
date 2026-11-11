package com.whitelabel.fixture.standalone

import com.whitelabel.core.shell.FoundationApplication

class FixtureApp : FoundationApplication() {
    override fun definition() = fixtureDefinition()
}
