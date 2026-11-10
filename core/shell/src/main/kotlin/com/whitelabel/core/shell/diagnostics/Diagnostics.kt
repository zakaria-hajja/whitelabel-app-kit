package com.whitelabel.core.shell.diagnostics

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.navigation.compose.composable
import com.whitelabel.core.shell.AppDefinitionBuilder
import com.whitelabel.core.shell.FeatureEntry
import com.whitelabel.core.shell.R
import com.whitelabel.core.shell.Tab
import kotlinx.serialization.Serializable

@Serializable
data object DiagnosticsRoute

/** Adds the diagnostics tab: app identity, a backend ping and the design-system catalog. */
fun AppDefinitionBuilder.diagnostics() {
    add(
        FeatureEntry(
            id = "diagnostics",
            tab = Tab(DiagnosticsRoute, R.string.shell_diagnostics_tab, Icons.Default.Info),
            routes = { composable<DiagnosticsRoute> { BrandPreviewRoot() } },
        ),
    )
}
