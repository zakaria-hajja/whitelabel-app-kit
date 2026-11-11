package com.whitelabel.core.testing.android

import androidx.test.platform.app.InstrumentationRegistry
import com.whitelabel.core.data.di.ApplicationScope
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Ends the app's `@ApplicationScope` after each test and waits for it. Each `@HiltAndroidTest`
 * gets a new singleton component in the same process, and a DataStore file can only be open
 * once: the previous test's stores hold their files until this scope ends. Put it inside
 * `HiltAndroidRule` (which creates the component) and outside the Compose rule (so the activity
 * has closed first).
 */
class EndAppScopeRule : TestRule {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    internal interface AppScope {
        @ApplicationScope fun scope(): CoroutineScope
    }

    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            try {
                base.evaluate()
            } finally {
                val job = EntryPoints.get(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext, AppScope::class.java).scope().coroutineContext.job
                job.cancel()
                runBlocking { job.join() }
            }
        }
    }
}
