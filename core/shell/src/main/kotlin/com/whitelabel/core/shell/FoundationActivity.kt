package com.whitelabel.core.shell

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.whitelabel.core.branding.BrandConfig
import com.whitelabel.core.designsystem.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** The launcher activity of every app: splash, theme and the app's definition in [ShellNavHost]. */
@AndroidEntryPoint
class FoundationActivity : ComponentActivity() {

    @Inject lateinit var definition: AppDefinition
    @Inject lateinit var brand: BrandConfig

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Phase 3 has no remote config: flags come from app.properties defaults only.
        val visibleTabs = definition.visibleTabs(emptyMap(), brand.runtimeDefaults.enabledFeatures)
        setContent {
            AppTheme(darkTheme = isSystemInDarkTheme()) {
                ShellNavHost(definition = definition, visibleTabs = visibleTabs)
            }
        }
    }
}
