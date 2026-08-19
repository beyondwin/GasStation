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
    ALL_INVALID,
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
    val graphModules = listOf(":graph:a", ":graph:b", ":graph:unresolved")
    val violationDependencies =
        listOf(
            Triple(":feature:sample", "implementation", ":data:sample"),
            Triple(":feature:nested:sample", "implementation", ":core:database"),
            Triple(":domain:sample", "api", ":core:network"),
            Triple(":feature:sample", "implementation", ":data:sample"),
        )
    val violationModules = violationDependencies.flatMap { listOf(it.first, it.third) }.distinct()
    val benchmarkMutations =
        if (mutation == TestedTargetMutation.ALL_INVALID) {
            linkedMapOf(
                ":benchmark-invalid" to TestedTargetMutation.ALL_INVALID,
                ":benchmark-missing" to TestedTargetMutation.MISSING,
                ":benchmark-valid-true" to TestedTargetMutation.VALID_TRUE,
                ":benchmark-valid-false" to TestedTargetMutation.VALID_FALSE,
            )
        } else {
            linkedMapOf(":benchmark" to mutation)
        }
    val androidModules =
        buildList {
            addAll(listOf(":app", ":library"))
            addAll(benchmarkMutations.keys)
            if (mutation in setOf(TestedTargetMutation.CHANGED, TestedTargetMutation.ALL_INVALID)) add(":other-app")
        }
    val modules = (androidModules + contractModules + graphModules + violationModules).sorted()
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
    writeFile(
        "gradle/libs.versions.toml",
        """
        [versions]
        kotlin = "2.4.10"

        [libraries]
        kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin" }
        """.trimIndent(),
    )
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

    benchmarkMutations.forEach { (benchmarkModule, benchmarkMutation) ->
        val directory = benchmarkModule.removePrefix(":")
        val actualTarget =
            if (benchmarkMutation in setOf(TestedTargetMutation.CHANGED, TestedTargetMutation.ALL_INVALID)) {
                ":other-app"
            } else {
                ":app"
            }
        val selfInstrumenting = benchmarkMutation != TestedTargetMutation.VALID_FALSE
        val dependencyMutation =
            when (benchmarkMutation) {
                TestedTargetMutation.EXTRA ->
                    "dependencies.add(project.dependencies.project(mapOf(\"path\" to \":core:model\")))"
                TestedTargetMutation.DUPLICATE ->
                    "dependencies.add(project.dependencies.project(mapOf(\"path\" to \":app\", \"configuration\" to \"default\")))"
                TestedTargetMutation.MISSING -> "dependencies.clear()"
                TestedTargetMutation.ALL_INVALID ->
                    "dependencies.add(project.dependencies.project(mapOf(\"path\" to \":app\"))); " +
                        "dependencies.add(project.dependencies.project(mapOf(\"path\" to \":app\", " +
                        "\"configuration\" to \"default\"))); " +
                        "dependencies.add(project.dependencies.project(mapOf(\"path\" to \":core:model\")))"
                else -> ""
            }
        writeFile(
            "$directory/build.gradle.kts",
            """
            plugins { id("com.android.test") }
            android {
                namespace = "fixture.${directory.replace('-', '_')}"
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
        writeFile("$directory/src/main/AndroidManifest.xml", "<manifest />")
    }

    val contractDependencies =
        mapOf(
            ":core:model" to emptyList(),
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
                appendLine("plugins { id(\"gasstation.jvm.library\") }")
                if (dependencies.isNotEmpty()) {
                    appendLine("dependencies {")
                    dependencies.forEach { appendLine("    api(project(\"$it\"))") }
                    appendLine("}")
                }
                if (module == ":domain:station") {
                    appendLine("dependencies { api(\"org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2\") }")
                }
            },
        )
        val directory = module.removePrefix(":").replace(':', '/')
        val packageName = "com.gasstation.${directory.replace('/', '.')}"
        val internalName = packageName.replace('.', '/') + "/Marker"
        writeFile(
            "$directory/src/main/kotlin/${packageName.replace('.', '/')}/Marker.kt",
            "package $packageName\n\npublic class Marker",
        )
        writeFile(
            "$directory/api/${module.substringAfterLast(':')}.api",
            "public final class $internalName {\n\tpublic fun <init> ()V\n}\n",
        )
        projectDir.resolve("$directory/api/${module.substringAfterLast(':')}.api").appendText("\n")
    }
    writeFile(
        "domain/station/src/main/kotlin/com/gasstation/domain/station/KotlinConsumer.kt",
        """
        package com.gasstation.domain.station

        import com.gasstation.core.model.Marker as ModelMarker
        import kotlinx.coroutines.flow.Flow

        internal class KotlinConsumer(
            private val marker: ModelMarker,
            private val stream: Flow<ModelMarker>,
        )
        """.trimIndent(),
    )
    writeFile(
        "domain/station/src/main/java/com/gasstation/domain/station/JavaConsumer.java",
        """
        package com.gasstation.domain.station;

        import com.gasstation.core.model.Marker;

        final class JavaConsumer {
            private final Marker marker;
            JavaConsumer(Marker marker) { this.marker = marker; }
        }
        """.trimIndent(),
    )
    mapOf(":graph:a" to ":graph:b", ":graph:b" to ":graph:a").forEach { (consumer, target) ->
        writeFile(
            "${consumer.removePrefix(":").replace(':', '/')}/build.gradle.kts",
            "plugins { `java-library` }\ndependencies { api(project(\"$target\")) }",
        )
    }
    writeFile(
        "graph/unresolved/build.gradle.kts",
        "plugins { `java-library` }\ndependencies { implementation(\"invalid.example:never-resolve:1.0\") }",
    )
    violationModules.forEach { module ->
        val dependencies = violationDependencies.filter { it.first == module }
        writeFile(
            "${module.removePrefix(":").replace(':', '/')}/build.gradle.kts",
            buildString {
                appendLine("plugins { `java-library` }")
                if (dependencies.isNotEmpty()) {
                    appendLine("dependencies {")
                    dependencies.forEach { (_, bucket, target) -> appendLine("    $bucket(project(\"$target\"))") }
                    appendLine("}")
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
            androidModules.sorted().forEach { consumer ->
                if (consumer in modules) {
                    val components = if (consumer.startsWith(":benchmark")) "benchmark,debug" else "debug,release"
                    scopes +=
                        "scope|$consumer|external|org.jetbrains.kotlin:kotlin-stdlib|api|" +
                            "compile=$components|runtime=$components"
                }
            }
            contractModules.forEach { consumer ->
                scopes +=
                    "scope|$consumer|external|org.jetbrains.kotlin:kotlin-stdlib|api|" +
                        "compile=main|runtime=main"
            }
            scopes +=
                "scope|:domain:station|external|org.jetbrains.kotlinx:kotlinx-coroutines-core|api|" +
                    "compile=main|runtime=main"
            if (mutation == TestedTargetMutation.VALID_FALSE) {
                scopes +=
                    "scope|:benchmark|project|:app|compileOnly|" +
                        "compile=benchmark,debug|runtime=-"
            } else if (mutation == TestedTargetMutation.ALL_INVALID) {
                scopes +=
                    "scope|:benchmark-valid-false|project|:app|compileOnly|" +
                        "compile=benchmark,debug|runtime=-"
            }
            contractDependencies.toSortedMap().forEach { (consumer, targets) ->
                targets.forEach { target ->
                    scopes += "scope|$consumer|project|$target|api|compile=main|runtime=main"
                }
            }
            listOf(":graph:a" to ":graph:b", ":graph:b" to ":graph:a").forEach { (consumer, target) ->
                scopes += "scope|$consumer|project|$target|api|compile=main|runtime=main"
            }
            scopes.sorted().forEach(::appendLine)
            val expectedConsumer =
                if (mutation == TestedTargetMutation.ALL_INVALID) ":benchmark-invalid" else ":benchmark"
            val expectedSelfInstrumenting = mutation != TestedTargetMutation.VALID_FALSE
            appendLine(
                "tested-target|$expectedConsumer|benchmark,debug|:app|" +
                    "self-instrumenting=$expectedSelfInstrumenting|" +
                    "compile-only-membership=${if (expectedSelfInstrumenting) "absent" else "present"}",
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
