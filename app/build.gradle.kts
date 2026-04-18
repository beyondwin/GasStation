plugins {
    id("gasstation.android.application.compose")
    id("gasstation.android.hilt")
}

val opinetApiKey = providers.gradleProperty("opinet.apikey").orElse("").get()
val kakaoApiKey = providers.gradleProperty("kakao.apikey").orElse("").get()

android {
    namespace = "com.gasstation"
    flavorDimensions += "environment"

    defaultConfig {
        applicationId = "com.gasstation"
        versionCode = 1
        versionName = "1.0"
    }

    productFlavors {
        create("demo") {
            dimension = "environment"
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
            testInstrumentationRunner = "com.gasstation.HiltTestRunner"
            buildConfigField("boolean", "DEMO_MODE", "true")
            buildConfigField("String", "OPINET_API_KEY", "\"\"")
            buildConfigField("String", "KAKAO_API_KEY", "\"\"")
        }
        create("prod") {
            dimension = "environment"
            buildConfigField("boolean", "DEMO_MODE", "false")
            buildConfigField("String", "OPINET_API_KEY", "\"$opinetApiKey\"")
            buildConfigField("String", "KAKAO_API_KEY", "\"$kakaoApiKey\"")
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
        }

        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":core:database"))
    implementation(project(":core:location"))
    implementation(project(":core:model"))
    implementation(project(":core:network"))
    implementation(project(":domain:station"))
    implementation(project(":data:settings"))
    implementation(project(":data:station"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:station-list"))
    implementation(project(":feature:watchlist"))
    implementation(project(":core:designsystem"))

    implementation(libs.timber)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.navigation.compose)

    testImplementation(libs.hilt.android.testing)
    testImplementation(libs.robolectric)
    kspTest(libs.hilt.android.compiler)

    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.android.compiler)
}
