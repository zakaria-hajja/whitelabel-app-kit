plugins {
    id("whitelabel.android.application")
}

android {
    namespace = "com.whitelabel.fixture.standalone"
}

dependencies {
    implementation(project(":core:shell"))
    implementation(project(":feature:settings"))
}
