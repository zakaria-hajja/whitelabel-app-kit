package com.whitelabel.core.shell

import android.app.Application
import com.whitelabel.core.analytics.CrashReporter
import com.whitelabel.core.data.di.ApplicationScope
import com.whitelabel.core.network.ModuleContributor
import com.whitelabel.core.shell.brand.adsWiringFor
import com.whitelabel.core.shell.brand.buildTypeModules
import com.whitelabel.core.shell.brand.firebaseWiringFor
import com.whitelabel.core.shell.brand.foundationModules
import com.whitelabel.core.shell.brand.recordsUncaughtCrashes
import java.util.ServiceLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

/**
 * Base for every app's Application class, which says what the app contains:
 * `class MyApp : FoundationApplication() { override fun definition() = myDefinition() }`.
 * Starts the app's Koin graph from that definition (plus any classpath [ModuleContributor], such
 * as the mock backend). Uncaught exceptions are recorded with the app's [CrashReporter] before
 * the default handler runs, except with Firebase on, where Crashlytics' handler reports them.
 */
abstract class FoundationApplication : Application() {

    /** Everything the app contains, built with `appDefinition { … }`. */
    abstract fun definition(): AppDefinition

    /** Whether the uncaught-exception handler records through [CrashReporter]; set by [startFoundation]. */
    @Volatile private var recordUncaught = true

    override fun onCreate() {
        super.onCreate()
        startFoundation()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (recordUncaught) runCatching { GlobalContext.get().get<CrashReporter>().record(throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Starts the app's graph. Called by [onCreate]; tests call it again after [stopFoundation]. */
    fun startFoundation() {
        val contributors = ServiceLoader.load(ModuleContributor::class.java, javaClass.classLoader).toList()
        val definition = definition()
        val firebase = firebaseWiringFor(this, definition, contributors)
        val ads = adsWiringFor(this, definition, contributors)
        recordUncaught = recordsUncaughtCrashes(firebase.mode)
        startKoin {
            androidContext(this@FoundationApplication)
            modules(foundationModules(definition, contributors, firebase, ads, buildTypeModules(this@FoundationApplication)))
        }
    }

    /**
     * Stops the graph and waits for the work it started (store writes) to end, so a test can delete
     * the app's files and start a fresh graph. A store file can be open only once at a time.
     */
    fun stopFoundation() {
        val job = GlobalContext.getOrNull()?.getOrNull<CoroutineScope>(ApplicationScope)?.coroutineContext?.job
        stopKoin()
        job?.let { runBlocking { it.join() } }
    }
}
