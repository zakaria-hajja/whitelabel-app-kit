plugins {
    id("whitelabel.kmp.library")
    id("whitelabel.koin")
}

// The iOS app's entry point into the shared code (Shared, IosPlatform, typed ViewModel accessors).
// Every app's framework exports it. iOS-only code lives in iosMain.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:shell:shared"))
            api(project(":core:data"))
            api(project(":core:network"))
            api(project(":core:datastore"))
            api(project(":core:analytics"))
            api(project(":core:branding"))
            api(project(":core:consent:shared"))
            api(project(":core:firebase:shared"))
            api(project(":feature:authentication:shared"))
            api(project(":feature:settings:shared"))
            api(project(":feature:profile:shared"))
            api(project(":feature:home:shared"))
            api(project(":feature:catalog:shared"))
            implementation(libs.ktor.client.core)
        }
    }
}
