package com.whitelabel.core.testing.android

import androidx.test.platform.app.InstrumentationRegistry
import com.whitelabel.core.shell.FoundationApplication
import java.io.File
import java.security.KeyStore
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Starts each test from a fresh install's state, with a fresh graph: stops the app's Koin graph
 * (and waits for its stores to close), deletes the stored files (`filesDir/datastore`: session,
 * cached user and config, preferences) and the session's Keystore key, then starts the graph
 * again. Put it outside the Compose rule in a `RuleChain`, so it runs before the activity starts.
 */
class FreshAppRule(private val keystoreAlias: String = "foundation_session") : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as FoundationApplication
            app.stopFoundation()
            File(app.filesDir, "datastore").deleteRecursively()
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.let { keys ->
                if (keys.containsAlias(keystoreAlias)) keys.deleteEntry(keystoreAlias)
            }
            app.startFoundation()
            base.evaluate()
        }
    }
}
