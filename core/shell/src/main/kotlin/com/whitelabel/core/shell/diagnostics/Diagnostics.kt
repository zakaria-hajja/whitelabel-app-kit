package com.whitelabel.core.shell.diagnostics

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/**
 * The diagnostics screen: app identity, a backend ping and the design-system catalog. The shell
 * registers it in DEVELOPMENT builds only; Settings › About links to it there.
 */
@Serializable
data object DiagnosticsRoute

internal fun NavGraphBuilder.diagnosticsScreen(onBack: () -> Unit) {
    composable<DiagnosticsRoute> { BrandPreviewRoot(onBack = onBack) }
}
