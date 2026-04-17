plugins {
    id("gasstation.android.library")
    id("gasstation.android.hilt")
}

android {
    namespace = "com.gasstation.core.location"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.play.services.location)
    testImplementation(libs.junit)
}
