plugins {
    id("gasstation.jvm.library")
}

dependencies {
    api(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)
    testImplementation(libs.app.cash.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
}
