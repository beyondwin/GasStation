plugins {
    id("gasstation.jvm.library")
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    api(project(":domain:station"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)
    testImplementation(libs.app.cash.turbine)
}
