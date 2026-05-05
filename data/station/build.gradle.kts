plugins {
    id("gasstation.android.library")
    id("gasstation.android.hilt")
}

android {
    namespace = "com.gasstation.data.station"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    implementation(project(":domain:station"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlin.test)
}
