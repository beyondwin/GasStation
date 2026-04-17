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
}

dependencies {
    implementation(project(":core:database"))
    implementation(project(":core:location"))
    implementation(project(":core:model"))
    implementation(project(":domain:station"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:station-list"))
    implementation(project(":data:settings"))
    implementation(project(":data:station"))
    implementation(project(":core:designsystem"))

    implementation(libs.androidx.appcompat)
    implementation(libs.play.services.location)
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.constraintlayout.compose)
    implementation(libs.androidx.material)
    implementation(libs.timber)
    implementation(libs.retrofit)
    implementation(libs.logging.interceptor)
    implementation(libs.converter.gson)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
}
