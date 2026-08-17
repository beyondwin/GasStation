pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "GasStation"

val activeModulePaths: List<String> = listOf(
    ":app",
    ":core:model",
    ":core:observability",
    ":core:designsystem",
    ":core:location",
    ":core:network",
    ":core:database",
    ":core:datastore",
    ":domain:location",
    ":domain:settings",
    ":domain:station",
    ":data:settings",
    ":data:station",
    ":feature:settings",
    ":feature:station-list",
    ":feature:watchlist",
    ":tools:demo-seed",
    ":benchmark",
)
include(*activeModulePaths.toTypedArray())
gradle.extensions.extraProperties.set(
    "gasstation.activeModulePaths",
    activeModulePaths.toList(),
)
