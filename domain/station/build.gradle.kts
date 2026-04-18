plugins {
    id("gasstation.jvm.library")
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)
    testImplementation(project(":core:common"))
    testImplementation(libs.app.cash.turbine)
}
