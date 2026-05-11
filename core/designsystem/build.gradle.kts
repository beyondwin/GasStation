plugins {
    id("gasstation.android.library.compose")
    id("gasstation.roborazzi")
}

android {
    namespace = "com.gasstation.core.designsystem"
}

dependencies {
    implementation(project(":core:model"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
}
