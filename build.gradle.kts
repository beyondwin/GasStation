import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

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

val coverageUnitTestTasks = listOf(
    ":domain:location:test",
    ":core:model:test",
    ":domain:station:test",
    ":domain:settings:test",
    ":core:database:testDebugUnitTest",
    ":core:datastore:testDebugUnitTest",
    ":core:designsystem:testDebugUnitTest",
    ":core:location:testDebugUnitTest",
    ":core:network:test",
    ":core:observability:test",
    ":data:settings:testDebugUnitTest",
    ":data:station:testDebugUnitTest",
    ":feature:settings:testDebugUnitTest",
    ":feature:station-list:testDebugUnitTest",
    ":feature:watchlist:testDebugUnitTest",
    ":app:testDemoDebugUnitTest",
    ":app:testProdDebugUnitTest",
    ":tools:demo-seed:test",
)
val coverageProjects = subprojects.filter { it.path != ":benchmark" }
val coverageExcludes = listOf(
    "**/*Hilt_*.*",
    "**/*_HiltModules*.*",
    "**/*_Factory*.*",
    "**/*_Provide*.*",
    "**/*ComposableSingletons*.*",
    "**/*Preview*Kt*.*",
)
val jvmCoverageProjects = setOf(
    ":core:model",
    ":core:network",
    ":core:observability",
    ":domain:location",
    ":domain:settings",
    ":domain:station",
    ":tools:demo-seed",
)

tasks.register<JacocoReport>("coverageXmlReport") {
    group = "verification"
    description = "Runs the complete unit-test matrix and writes the aggregated JaCoCo XML report."
    dependsOn(
        coverageUnitTestTasks,
    )
    executionData.from(
        coverageProjects.map { project ->
            project.fileTree(project.layout.buildDirectory) {
                include(
                    "jacoco/*.exec",
                    "outputs/unit_test_code_coverage/**/*.exec",
                )
            }
        },
    )
    sourceDirectories.from(
        coverageProjects.flatMap { project ->
            listOf(
                project.layout.projectDirectory.dir("src/main/kotlin"),
                project.layout.projectDirectory.dir("src/main/java"),
                project.layout.projectDirectory.dir("src/demo/kotlin"),
                project.layout.projectDirectory.dir("src/prod/kotlin"),
            )
        },
    )
    classDirectories.from(
        coverageProjects.flatMap { project ->
            if (project.path in jvmCoverageProjects) {
                listOf(
                    project.fileTree(project.layout.buildDirectory.dir("classes/kotlin/main")) {
                        exclude(coverageExcludes)
                    },
                    project.fileTree(project.layout.buildDirectory.dir("classes/java/main")) {
                        exclude(coverageExcludes)
                    },
                )
            } else {
                val variant = if (project.path == ":app") "demoDebug" else "debug"
                buildList {
                    add(
                        project.fileTree(
                            project.layout.buildDirectory.dir(
                                "intermediates/built_in_kotlinc/$variant/compile${variant.replaceFirstChar(Char::uppercase)}Kotlin/classes",
                            ),
                        ) {
                            exclude(coverageExcludes)
                        },
                    )
                    if (project.path == ":app") {
                        add(
                            project.fileTree(
                                project.layout.buildDirectory.dir(
                                    "intermediates/built_in_kotlinc/prodDebug/compileProdDebugKotlin/classes",
                                ),
                            ) {
                                include(
                                    "com/gasstation/analytics/LogcatCrashReporter*.class",
                                    "com/gasstation/di/ProdCrashReporterModule*.class",
                                    "com/gasstation/di/ProdStartupModule*.class",
                                    "com/gasstation/startup/ProdSecretsStartupHook*.class",
                                )
                                exclude(coverageExcludes)
                            },
                        )
                    }
                }
            }
        },
    )
    reports {
        xml.required.set(true)
        xml.outputLocation.set(layout.buildDirectory.file("reports/coverage/report.xml"))
        html.required.set(false)
        csv.required.set(false)
    }
}
