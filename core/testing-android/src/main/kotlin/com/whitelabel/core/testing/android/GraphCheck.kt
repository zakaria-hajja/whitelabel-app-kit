package com.whitelabel.core.testing.android

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlin.reflect.KClass
import org.koin.core.Koin
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.parameter.ParametersHolder
import org.koin.core.parameter.parametersOf

/**
 * Resolves every ViewModel bound in this graph and fails, naming each one, if any can't be built.
 * Koin finds a missing binding only when a screen opens; an app's device test calls this on the
 * real graph (`GlobalContext.get()`) so no screen has to. Each ViewModel gets a fresh
 * `SavedStateHandle`; one that takes other parameters gets them from [parameters] (return null to
 * keep the default). Returns the ViewModel classes it resolved.
 */
@OptIn(KoinInternalApi::class)
fun Koin.assertEveryViewModelResolves(parameters: (KClass<*>) -> ParametersHolder? = { null }): List<KClass<*>> {
    val definitions = instanceRegistry.instances.values.map { it.beanDefinition }
        .filter { ViewModel::class.java.isAssignableFrom(it.primaryType.java) }
        .distinctBy { it.primaryType to it.qualifier }
    if (definitions.isEmpty()) throw AssertionError("No ViewModel definitions in the graph")
    val failures = definitions.mapNotNull { definition ->
        runCatching {
            get<Any>(definition.primaryType, definition.qualifier) {
                parameters(definition.primaryType) ?: parametersOf(SavedStateHandle())
            }
        }.exceptionOrNull()?.let { "${definition.primaryType.qualifiedName}: ${it.message}" }
    }
    if (failures.isNotEmpty()) throw AssertionError("ViewModels that can't be built:\n" + failures.joinToString("\n"))
    return definitions.map { it.primaryType }
}
