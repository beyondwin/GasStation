pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "GasStation"

include(
    ":app",
    ":core:common",
    ":core:model",
    ":core:designsystem",
    ":core:location",
    ":core:network",
    ":core:database",
    ":core:datastore",
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
