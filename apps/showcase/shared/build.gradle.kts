plugins {
    id("whitelabel.kmp.library")
    // Packages every shared module's Compose resources (their strings) with the iOS binaries, so the
    // ViewModels' messages resolve in the iOS app.
    id("whitelabel.kmp.resources")
    id("whitelabel.koin")
    // StateFlow → Swift async sequences, sealed types → Swift enums, suspend → async.
    alias(libs.plugins.skie)
    id("whitelabel.ios.app")
}

// Showcase's shared code as one iOS framework. Every app's framework is named "Shared", so the
// reusable Swift kit (ios/WhitelabelKit) imports the same module whatever the app.
val exported = listOf(
    ":core:common", ":core:domain", ":core:presentation", ":core:data", ":core:network", ":core:datastore",
    ":core:branding", ":core:analytics", ":core:consent:shared",
    ":core:firebase:shared", ":core:shell:shared", ":core:ios",
    ":feature:authentication:shared", ":feature:settings:shared", ":feature:profile:shared",
    ":feature:home:shared", ":feature:catalog:shared",
)

// -Pwhitelabel.mockApi=true (passed only by the Xcode Debug configuration) links the fake backend;
// Release builds never contain it.
val withMockApi = providers.gradleProperty("whitelabel.mockApi").orNull == "true"
val linked = if (withMockApi) exported + ":core:ios:mock" else exported

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
            linked.forEach { export(project(it)) }
        }
    }
    sourceSets {
        commonMain.dependencies {
            linked.forEach { api(project(it)) }
        }
    }
}

iosApp {
    androidApp.set(rootProject.layout.projectDirectory.dir("apps/showcase"))
    iosConfigDir.set(rootProject.layout.projectDirectory.dir("ios/Showcase/Config"))
}
