plugins {
    id("whitelabel.kmp.library")
    // Packages every shared module's Compose resources (their strings) with the iOS binaries, so the
    // ViewModels' messages resolve in the iOS app.
    id("whitelabel.kmp.resources")
    id("whitelabel.koin")
}

// Showcase's shared code as one iOS framework (ShowcaseShared), for the iOS app (step 5 of the iOS
// design). The modules Swift calls are exported, so their types appear in the framework's API.
val exported = listOf(
    ":core:common", ":core:domain", ":core:presentation", ":core:data", ":core:network", ":core:datastore",
    ":core:branding", ":core:analytics",
    ":feature:authentication:shared", ":feature:settings:shared", ":feature:profile:shared",
    ":feature:home:shared", ":feature:catalog:shared",
)

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ShowcaseShared"
            isStatic = true
            exported.forEach { export(project(it)) }
        }
    }
    sourceSets {
        commonMain.dependencies {
            exported.forEach { api(project(it)) }
        }
        iosTest.dependencies {
            implementation(libs.ktor.client.mock)
            implementation(libs.androidx.lifecycle.viewmodel.savedstate)
        }
    }
}
