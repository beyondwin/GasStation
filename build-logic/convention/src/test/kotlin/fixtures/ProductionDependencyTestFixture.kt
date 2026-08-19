package com.gasstation.buildlogic.testing

import java.io.File
import java.util.Properties

enum class TestedTargetMutation {
    VALID_TRUE,
    VALID_FALSE,
    EXTRA,
    DUPLICATE,
    MISSING,
    CHANGED,
    UNRESOLVED,
}

fun GradlePluginTestProject.writeProductionDependencyAndroidFixture(
    mutation: TestedTargetMutation,
): GradlePluginTestProject {
    val contractModules =
        listOf(
            ":core:model",
            ":core:observability",
            ":domain:location",
            ":domain:settings",
            ":domain:station",
        )
    val androidModules =
        buildList {
            addAll(listOf(":app", ":benchmark", ":library"))
            if (mutation == TestedTargetMutation.CHANGED) add(":other-app")
        }
    val modules = (androidModules + contractModules).sorted()
    writeSettings(
        """
        pluginManagement {
            repositories {
                google()
                mavenCentral()
                gradlePluginPortal()
            }
        }

        dependencyResolutionManagement {
            repositories {
                google()
                mavenCentral()
                gradlePluginPortal()
            }
        }

        rootProject.name = "production-dependency-android-fixture"
        val activeModulePaths: List<String> = listOf(${modules.joinToString { "\"$it\"" }})
        include(*activeModulePaths.toTypedArray())
        gradle.extensions.extraProperties.set("gasstation.activeModulePaths", activeModulePaths.toList())
        """.trimIndent(),
    )
    writeFile("local.properties", "sdk.dir=${productionFixtureAndroidSdk().escapeProperties()}")
    writeBuildFile(
        """
        plugins {
            id("gasstation.root.quality")
        }
        """.trimIndent(),
    )
    listOf(":app", ":other-app").filter { it in modules }.forEach { module ->
        val directory = module.removePrefix(":")
        writeFile(
            "$directory/build.gradle.kts",
            """
            plugins { id("com.android.application") }
            android {
                namespace = "fixture.${directory.replace('-', '.')}"
                compileSdk = 37
                defaultConfig {
                    applicationId = "fixture.${directory.replace('-', '.')}"
                    minSdk = 24
                    targetSdk = 36
                }
            }
            """.trimIndent(),
        )
        writeFile("$directory/src/main/AndroidManifest.xml", "<manifest />")
    }
    writeFile(
        "library/build.gradle.kts",
        """
        plugins { id("com.android.library") }
        android {
            namespace = "fixture.library"
            compileSdk = 37
            defaultConfig { minSdk = 24 }
        }
        """.trimIndent(),
    )
    writeFile("library/src/main/AndroidManifest.xml", "<manifest />")

    val actualTarget = if (mutation == TestedTargetMutation.CHANGED) ":other-app" else ":app"
    val selfInstrumenting = mutation != TestedTargetMutation.VALID_FALSE
    val dependencyMutation =
        when (mutation) {
            TestedTargetMutation.EXTRA ->
                "dependencies.add(project.dependencies.project(mapOf(\"path\" to \":core:model\")))"
            TestedTargetMutation.DUPLICATE ->
                "dependencies.add(project.dependencies.project(mapOf(\"path\" to \":app\", \"configuration\" to \"default\")))"
            TestedTargetMutation.MISSING -> "dependencies.clear()"
            else -> ""
        }
    writeFile(
        "benchmark/build.gradle.kts",
        """
        plugins { id("com.android.test") }
        android {
            namespace = "fixture.benchmark"
            compileSdk = 37
            targetProjectPath = "$actualTarget"
            experimentalProperties["android.experimental.self-instrumenting"] = $selfInstrumenting
            defaultConfig { minSdk = 24 }
            buildTypes { create("benchmark") { initWith(getByName("debug")) } }
        }
        ${
            if (dependencyMutation.isBlank()) {
                ""
            } else {
                "afterEvaluate { configurations.getByName(\"testedApks\").run { $dependencyMutation } }"
            }
        }
        """.trimIndent(),
    )
    writeFile("benchmark/src/main/AndroidManifest.xml", "<manifest />")

    val contractDependencies =
        mapOf(
            ":core:model" to listOf(":domain:station"),
            ":core:observability" to listOf(":core:model"),
            ":domain:location" to listOf(":core:observability"),
            ":domain:settings" to listOf(":core:observability"),
            ":domain:station" to listOf(":core:observability", ":domain:location", ":domain:settings"),
        )
    contractModules.forEach { module ->
        val dependencies = contractDependencies[module].orEmpty()
        writeFile(
            "${module.removePrefix(":").replace(':', '/')}/build.gradle.kts",
            buildString {
                appendLine("plugins { `java-library` }")
                if (dependencies.isNotEmpty()) {
                    appendLine("dependencies {")
                    dependencies.forEach { appendLine("    api(project(\"$it\"))") }
                    appendLine("}")
                }
                if (mutation == TestedTargetMutation.UNRESOLVED && module == ":domain:station") {
                    appendLine("dependencies { implementation(\"invalid.example:never-resolve:1.0\") }")
                }
            },
        )
    }
    writeFile(
        "config/quality/production-dependency-policy.txt",
        buildString {
            appendLine("schema-version=1")
            appendLine("enforcement=blocking")
            modules.forEach { appendLine("module|$it") }
            val scopes = mutableListOf<String>()
            listOf(":app", ":benchmark", ":library").forEach { consumer ->
                if (consumer in modules) {
                    val components = if (consumer == ":benchmark") "benchmark,debug" else "debug,release"
                    scopes +=
                        "scope|$consumer|external|org.jetbrains.kotlin:kotlin-stdlib|api|" +
                            "compile=$components|runtime=$components"
                }
            }
            if (!selfInstrumenting) {
                scopes +=
                    "scope|:benchmark|project|:app|compileOnly|" +
                        "compile=benchmark,debug|runtime=-"
            }
            contractDependencies.toSortedMap().forEach { (consumer, targets) ->
                targets.forEach { target ->
                    scopes += "scope|$consumer|project|$target|api|compile=main|runtime=main"
                }
            }
            scopes.sorted().forEach(::appendLine)
            appendLine(
                "tested-target|:benchmark|benchmark,debug|:app|self-instrumenting=$selfInstrumenting|" +
                    "compile-only-membership=${if (selfInstrumenting) "absent" else "present"}",
            )
        },
    )
    return this
}

private fun productionFixtureAndroidSdk(): String {
    sequenceOf("ANDROID_HOME", "ANDROID_SDK_ROOT")
        .mapNotNull(System::getenv)
        .firstOrNull(String::isNotBlank)
        ?.let { return File(it).canonicalPath }
    val localProperties =
        generateSequence(File(System.getProperty("user.dir")).canonicalFile, File::getParentFile)
            .map { it.resolve("local.properties") }
            .firstOrNull(File::isFile)
        ?: error("Android fixture requires a configured SDK")
    return Properties().apply { localProperties.inputStream().use(::load) }
        .getProperty("sdk.dir")
        .let(::File)
        .canonicalPath
}

private fun String.escapeProperties(): String = replace("\\", "\\\\").replace(":", "\\:")
