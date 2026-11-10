plugins {
    id("whitelabel.jvm.library")
}

dependencies {
    api(project(":core:common"))
    api(libs.kotlinx.coroutines.core)
}
