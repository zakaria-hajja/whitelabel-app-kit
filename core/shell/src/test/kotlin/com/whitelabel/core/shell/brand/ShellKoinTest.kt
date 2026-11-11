package com.whitelabel.core.shell.brand

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import com.whitelabel.core.branding.Environment
import com.whitelabel.core.branding.RuntimeDefaults
import com.whitelabel.core.mockapi.MockApiContributor
import com.whitelabel.core.shell.RouteA
import com.whitelabel.core.shell.appDefinition
import com.whitelabel.core.shell.entry
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.okhttp.OkHttpEngine
import java.io.File
import org.junit.jupiter.api.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.verify.verify

@OptIn(KoinExperimentalAPI::class)
class ShellKoinTest {

    private val definition = appDefinition { add(entry("a", RouteA)) }

    @Test fun `the foundation graph resolves with only the platform's context`() {
        // verify() checks the constructor of every definition, even one built in a lambda from
        // platform values: BrandConfig and NetworkConfig are built from resources, store files come
        // from StorePaths. Their value types are listed so only real bindings are checked.
        module { includes(foundationModules(definition, emptyList())) }
            .verify(
                extraTypes = listOf(
                    Context::class, SavedStateHandle::class, File::class,
                    String::class, Environment::class, RuntimeDefaults::class, Function1::class,
                ),
            )
    }

    @Test fun `a contributor's engine override replaces OkHttp and flags the mock api`() {
        val koin = koinApplication { modules(foundationModules(definition, listOf(MockApiContributor()))) }.koin
        assertThat(koin.get<HttpClientEngine>()).isInstanceOf(MockEngine::class)
        assertThat(koin.get<Boolean>(named(USING_MOCK_API))).isTrue()
    }

    @Test fun `without a contributor the engine is OkHttp and the mock api flag is off`() {
        val koin = koinApplication { modules(foundationModules(definition, emptyList())) }.koin
        assertThat(koin.get<HttpClientEngine>()).isInstanceOf(OkHttpEngine::class)
        assertThat(koin.get<Boolean>(named(USING_MOCK_API))).isFalse()
    }
}
