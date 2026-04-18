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
}
