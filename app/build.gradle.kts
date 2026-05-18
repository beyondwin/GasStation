plugins {
    id("gasstation.android.application.compose")
    id("gasstation.android.hilt")
}

val opinetApiKey = providers.gradleProperty("opinet.apikey").orElse("").get()

android {
    namespace = "com.gasstation"
    flavorDimensions += "environment"

    defaultConfig {
        applicationId = "com.gasstation"
        versionCode = 7
        versionName = "1.1.3"
    }

    productFlavors {
        create("demo") {
            dimension = "environment"
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
            testInstrumentationRunner = "com.gasstation.HiltTestRunner"
            buildConfigField("boolean", "DEMO_MODE", "true")
            buildConfigField("String", "OPINET_API_KEY", "\"\"")
        }
        create("prod") {
            dimension = "environment"
            buildConfigField("boolean", "DEMO_MODE", "false")
            buildConfigField("String", "OPINET_API_KEY", "\"$opinetApiKey\"")
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
        }

        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }

        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
            isProfileable = true
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
    implementation(project(":core:observability"))
    implementation(project(":core:network"))
    implementation(project(":domain:settings"))
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
    implementation(libs.androidx.profileinstaller)

    testImplementation(libs.hilt.android.testing)
    testImplementation(libs.robolectric)
    kspTest(libs.hilt.android.compiler)

    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.android.compiler)
}
