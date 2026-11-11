plugins {
    id("whitelabel.android.application")
}

android {
    namespace = "com.whitelabel.apps.showcase"
}

dependencies {
    implementation(project(":core:shell"))
    implementation(project(":feature:authentication"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:profile"))
    implementation(project(":feature:home"))
    implementation(project(":feature:catalog"))
}
