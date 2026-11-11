package com.whitelabel.core.testing.android

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.File
import java.security.KeyStore

/**
 * Starts each test from a fresh install's state: deletes the app's stored files
 * (`filesDir/datastore`: session, cached user and config, preferences) and the session's
 * Keystore key. Put it outside `HiltAndroidRule` in a `RuleChain`, so it runs before the app's
 * singletons are created.
 */
class ResetAppDataRule(private val keystoreAlias: String = "foundation_session") : TestRule {

    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            reset()
            base.evaluate()
        }
    }

    private fun reset() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir, "datastore").deleteRecursively()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.let { keys ->
            if (keys.containsAlias(keystoreAlias)) keys.deleteEntry(keystoreAlias)
        }
    }
}
