plugins {
    id("gasstation.android.library.compose")
    id("gasstation.android.hilt")
}

android {
    namespace = "com.gasstation.feature.settings"
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":domain:settings"))
    implementation(project(":domain:station"))
    implementation(project(":core:ui"))
    implementation(project(":core:designsystem"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.ui.tooling)
}
