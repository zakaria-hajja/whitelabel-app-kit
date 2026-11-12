package com.whitelabel.apps.showcase

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.whitelabel.core.domain.remote.DefaultsOnlyRemoteValues
import org.junit.jupiter.api.Test

/** Showcase turns Firebase on (Debug builds only decide at runtime, from app.properties) and reads one remote note. */
class ShowcaseFirebaseTest {

    @Test fun `showcase declares firebase`() {
        assertThat(showcaseDefinition().usesFirebase).isEqualTo(true)
    }

    @Test fun `remote note falls back to the default`() {
        assertThat(remoteNote(DefaultsOnlyRemoteValues)).isEqualTo("default")
        assertThat(REMOTE_NOTE_KEY).isEqualTo("showcase_remote_note")
    }
}
