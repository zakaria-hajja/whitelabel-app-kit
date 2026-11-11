plugins {
    id("whitelabel.jvm.library")
}

// Test fixtures only: modules add it as testImplementation, never to production code.
dependencies {
    api(libs.kotlinx.serialization.json)
    api(libs.json.schema.validator)
    api(project(":core:network"))
    api(libs.ktor.client.core)
    api(libs.ktor.client.mock)

    testImplementation(project(":core:common"))
}
