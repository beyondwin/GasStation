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
    implementation(libs.findLibrary("pitest-gradlePlugin").get())
    testImplementation(gradleTestKit())
    testImplementation(libs.findLibrary("junit").get())
}

// TestKit projects intentionally exercise dependency versions which are not selected by the
// production graph. Keep each seed isolated so Gradle conflict resolution cannot hide an older
// fixture version from dependency-verification metadata generation.
val testKitVerificationSeeds =
    listOf(
        "com.google.android:android:4.1.1.4",
        "androidx.annotation:annotation-experimental:1.1.0",
        "androidx.annotation:annotation-jvm:1.7.0",
        "androidx.core:core-ktx:1.8.0",
        "com.google.guava:guava:33.4.0-jre",
        "com.google.guava:guava:33.4.8-jre",
        "org.junit:junit-bom:5.10.2",
        "org.junit:junit-bom:5.11.0-M2",
        "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2",
        "org.jetbrains.kotlinx:kotlinx-coroutines-test-jvm:1.9.0",
    ).mapIndexed { index, coordinate ->
        configurations.create("testKitVerificationSeed$index") {
            isCanBeConsumed = false
            isCanBeResolved = true
        }.also { configuration -> dependencies.add(configuration.name, coordinate) }
    }

tasks.register("captureTestKitDependencyVerificationMetadata") {
    group = "verification"
    description = "Captures the reviewed TestKit-only dependency graph for checksum generation."
    doLast {
        testKitVerificationSeeds.forEach { configuration -> configuration.files.forEach(File::getName) }
    }
}

tasks.withType<Test>().configureEach {
    timeout.set(Duration.ofMinutes(15))
    maxParallelForks = 5
    systemProperty("gasstation.convention.test.maxParallelForks", maxParallelForks)
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
        register("gasStationJvmMutation") {
            id = "gasstation.jvm.mutation"
            implementationClass =
                "com.gasstation.buildlogic.quality.mutation.GasStationJvmMutationConventionPlugin"
        }
    }
}
