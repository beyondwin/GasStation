import java.time.Duration
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test

plugins {
    `kotlin-dsl`
}

group = "com.gasstation.buildlogic"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    implementation(libs.findLibrary("android-gradlePlugin").get())
    implementation(libs.findLibrary("kotlin-gradlePlugin").get())
    implementation(libs.findLibrary("kotlin-compose-gradlePlugin").get())
    implementation(libs.findLibrary("ksp-gradlePlugin").get())
    implementation(libs.findLibrary("hilt-gradlePlugin").get())
    implementation(libs.findLibrary("spotless-gradlePlugin").get())
    implementation(libs.findLibrary("roborazzi-gradlePlugin").get())
    implementation("org.jacoco:org.jacoco.core:0.8.15")
    implementation(libs.findLibrary("asm").get())
    testImplementation(gradleTestKit())
    testImplementation(libs.findLibrary("junit").get())
}

tasks.withType<Test>().configureEach {
    timeout.set(Duration.ofMinutes(15))
    maxParallelForks = 3
}

gradlePlugin {
    plugins {
        register("gasStationAndroidApplicationCompose") {
            id = "gasstation.android.application.compose"
            implementationClass = "GasStationAndroidApplicationComposeConventionPlugin"
        }
        register("gasStationAndroidLibrary") {
            id = "gasstation.android.library"
            implementationClass = "GasStationAndroidLibraryConventionPlugin"
        }
        register("gasStationAndroidLibraryCompose") {
            id = "gasstation.android.library.compose"
            implementationClass = "GasStationAndroidLibraryComposeConventionPlugin"
        }
        register("gasStationJvmLibrary") {
            id = "gasstation.jvm.library"
            implementationClass = "GasStationJvmLibraryConventionPlugin"
        }
        register("gasStationAndroidHilt") {
            id = "gasstation.android.hilt"
            implementationClass = "GasStationAndroidHiltConventionPlugin"
        }
        register("gasStationAndroidRoom") {
            id = "gasstation.android.room"
            implementationClass = "GasStationAndroidRoomConventionPlugin"
        }
        register("androidSpotless") {
            id = "gasstation.spotless"
            implementationClass = "GasStationSpotlessConventionPlugin"
        }
        register("roborazzi") {
            id = "gasstation.roborazzi"
            implementationClass = "GasStationRoborazziConventionPlugin"
        }
        register("gasStationRootQuality") {
            id = "gasstation.root.quality"
            implementationClass =
                "com.gasstation.buildlogic.quality.GasStationRootQualityConventionPlugin"
        }
    }
}
