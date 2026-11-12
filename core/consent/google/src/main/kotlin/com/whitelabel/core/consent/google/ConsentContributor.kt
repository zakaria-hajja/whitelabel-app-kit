package com.whitelabel.core.consent.google

import android.app.Application
import android.content.pm.ApplicationInfo
import com.whitelabel.core.consent.ConsentPort
import com.whitelabel.core.network.ModuleContributor
import java.io.File
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/** Binds [ConsentPort] to UMP when this module is on the classpath (found by `ServiceLoader`). */
class ConsentContributor : ModuleContributor {
    override fun modules(): List<Module> = listOf(
        module {
            // One instance, created at start so no resumed activity is missed; registered exactly once.
            single(createdAtStart = true) {
                CurrentActivity().also { (androidContext() as Application).registerActivityLifecycleCallbacks(it) }
            }
            single<ConsentPort> {
                val context = androidContext()
                val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
                UmpConsentPort(
                    context,
                    get(),
                    debugGeographyEea = debuggable && File(context.filesDir, "debug/$DEBUG_EEA_MARKER").exists(),
                )
            }
        },
    )
}
