import org.gradle.testing.jacoco.plugins.JacocoPluginExtension

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
    jacoco
    id("gasstation.root.quality")
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.androidTest) apply false
    alias(libs.plugins.jetbrainsKotlinJvm) apply false
    alias(libs.plugins.googleDevtoolsKsp) apply false
    alias(libs.plugins.googleDaggerHiltAndroid) apply false
    alias(libs.plugins.spotless) apply false
}

jacoco {
    toolVersion = "0.8.15"
}

subprojects {
    if (path != ":benchmark") {
        pluginManager.apply("jacoco")
        extensions.configure<JacocoPluginExtension> {
            toolVersion = "0.8.15"
        }
    }
}
