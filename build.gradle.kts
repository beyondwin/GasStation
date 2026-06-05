import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

buildscript {
    dependencies {
        classpath(libs.kotlin.gradlePlugin) {
            version {
                strictly(libs.versions.kotlin.get())
            }
        }
        classpath(libs.kotlin.compose.gradlePlugin) {
            version {
                strictly(libs.versions.kotlin.get())
            }
        }
        classpath(libs.ksp.gradlePlugin) {
            version {
                strictly(libs.versions.ksp.get())
            }
        }
    }
}

plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.androidTest) apply false
    alias(libs.plugins.jetbrainsKotlinJvm) apply false
    alias(libs.plugins.googleDevtoolsKsp) apply false
    alias(libs.plugins.googleDaggerHiltAndroid) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.benManesVersions)
}

dependencies {
    kover(project(":app"))
    kover(project(":core:model"))
    kover(project(":core:observability"))
    kover(project(":core:designsystem"))
    kover(project(":core:location"))
    kover(project(":core:network"))
    kover(project(":core:database"))
    kover(project(":core:datastore"))
    kover(project(":domain:location"))
    kover(project(":domain:settings"))
    kover(project(":domain:station"))
    kover(project(":data:settings"))
    kover(project(":data:station"))
    kover(project(":feature:settings"))
    kover(project(":feature:station-list"))
    kover(project(":feature:watchlist"))
    kover(project(":tools:demo-seed"))
}

kover {
    reports {
        filters {
            excludes {
                classes(
                    "*Hilt_*",
                    "*_HiltModules*",
                    "*_Factory*",
                    "*_Provide*",
                    "*ComposableSingletons*",
                    "*Preview*Kt",
                )
            }
        }
    }
}

fun isNonStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
    val regex = "^[0-9,.v-]+(-r)?$".toRegex()
    return !stableKeyword && !regex.matches(version)
}

tasks.withType<DependencyUpdatesTask>().configureEach {
    rejectVersionIf { isNonStable(candidate.version) }
}
