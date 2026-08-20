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
    COMPILE_ONLY_EXTRA,
    COMPILE_ONLY_MISSING,
    COMPILE_ONLY_DUPLICATE,
    COMPILE_ONLY_CHANGED,
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
        )
    val violationModules = violationDependencies.flatMap { listOf(it.first, it.third) }.distinct()
    val benchmarkMutations =
        if (mutation == TestedTargetMutation.ALL_INVALID) {
            linkedMapOf(
                ":benchmark-invalid" to TestedTargetMutation.ALL_INVALID,
                ":benchmark-missing" to TestedTargetMutation.MISSING,
                ":benchmark-valid-true" to TestedTargetMutation.VALID_TRUE,
                ":benchmark-valid-false" to TestedTargetMutation.VALID_FALSE,
                ":benchmark-compile-extra" to TestedTargetMutation.COMPILE_ONLY_EXTRA,
                ":benchmark-compile-missing" to TestedTargetMutation.COMPILE_ONLY_MISSING,
                ":benchmark-compile-duplicate" to TestedTargetMutation.COMPILE_ONLY_DUPLICATE,
                ":benchmark-compile-changed" to TestedTargetMutation.COMPILE_ONLY_CHANGED,
            )
        } else {
            linkedMapOf(":benchmark" to mutation)
        }
    val androidModules =
        buildList {
            addAll(listOf(":app", ":library"))
            addAll(benchmarkMutations.keys)
            if (
                mutation in setOf(
                    TestedTargetMutation.CHANGED,
                    TestedTargetMutation.COMPILE_ONLY_CHANGED,
                    TestedTargetMutation.ALL_INVALID,
                )
            ) add(":other-app")
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

        tasks.register("wiredUpdateMutation") {
            dependsOn(":core:model:updateKotlinAbi")
        }

        """.trimIndent(),
    )
    writeFile("config/quality/public-api-signatures.txt", "schema-version=1\n")
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
            flavorDimensions += "mode"
            productFlavors {
                create("demo") { dimension = "mode" }
                create("prod") { dimension = "mode" }
            }
        }
        configurations.create("ksp")
        dependencies {
            add("demoImplementation", project(":core:model"))
            add("debugImplementation", project(":core:observability"))
            add("testImplementation", project(":data:sample"))
            add("androidTestImplementation", project(":data:sample"))
            add("ksp", project(":data:sample"))
        }
        afterEvaluate {
            dependencies.add(
                "demoDebugImplementation",
                dependencies.project(mapOf("path" to ":domain:location")),
            )
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
        val selfInstrumenting = benchmarkMutation !in setOf(
            TestedTargetMutation.VALID_FALSE,
            TestedTargetMutation.COMPILE_ONLY_EXTRA,
            TestedTargetMutation.COMPILE_ONLY_MISSING,
            TestedTargetMutation.COMPILE_ONLY_DUPLICATE,
            TestedTargetMutation.COMPILE_ONLY_CHANGED,
        )
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
        val compileOnlyMutation =
            when (benchmarkMutation) {
                TestedTargetMutation.COMPILE_ONLY_EXTRA ->
                    "add(project.dependencies.project(mapOf(\"path\" to \":core:model\")))"
                TestedTargetMutation.COMPILE_ONLY_MISSING ->
                    "removeAll { it is org.gradle.api.artifacts.ProjectDependency && it.path == \":app\" }"
                TestedTargetMutation.COMPILE_ONLY_DUPLICATE ->
                    "add(project.dependencies.project(mapOf(\"path\" to \":app\", \"configuration\" to \"default\")))"
                TestedTargetMutation.COMPILE_ONLY_CHANGED ->
                    "removeAll { it is org.gradle.api.artifacts.ProjectDependency && it.path == \":app\" }; " +
                        "add(project.dependencies.project(mapOf(\"path\" to \":other-app\", \"configuration\" to \"default\")))"
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
            ${
                if (compileOnlyMutation.isBlank()) {
                    ""
                } else {
                    "afterEvaluate { configurations.getByName(\"compileOnly\").dependencies.run { $compileOnlyMutation } }"
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
                    appendLine("dependencies {")
                    appendLine("    api(\"org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2\")")
                    appendLine("    compileOnly(\"com.google.android:android:4.1.1.4\")")
                    appendLine("}")
                }
                if (module == ":core:model") {
                    appendLine("tasks.named(\"checkKotlinAbi\") { mustRunAfter(tasks.named(\"updateKotlinAbi\")) }")
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
        val abi =
            buildString {
                append("public final class $internalName {\n\tpublic fun <init> ()V\n}\n")
                if (module == ":domain:station") {
                    append(
                        "\npublic abstract interface class " +
                            "com/gasstation/domain/station/PublicSignatureContract {\n" +
                            "\tpublic abstract fun transform " +
                            "(Lkotlin/jvm/functions/Function1;Lkotlin/coroutines/Continuation;)" +
                            "Ljava/lang/Object;\n}\n",
                    )
                    append(
                        "\npublic abstract interface class " +
                            "com/gasstation/domain/station/RequiredGenericContract {\n" +
                            "\tpublic abstract fun required ()Ljava/util/List;\n}\n",
                    )
                }
            }
        writeFile(
            "$directory/api/${module.substringAfterLast(':')}.api",
            abi,
        )
        projectDir.resolve("$directory/api/${module.substringAfterLast(':')}.api").appendText("\n")
    }
    writeFile(
        "domain/station/src/main/kotlin/com/gasstation/domain/station/KotlinConsumer.kt",
        """
        package com.gasstation.domain.station

        import kotlinx.coroutines.flow.Flow
        import com.gasstation.core.model.Marker as ModelMarker

        internal class KotlinConsumer(
            private val marker: ModelMarker,
            private val stream: Flow<ModelMarker>,
        )
        """.trimIndent(),
    )
    writeFile(
        "domain/station/src/main/kotlin/com/gasstation/domain/station/PublicSignatureContract.kt",
        """
        package com.gasstation.domain.station

        import android.os.Parcelable

        public interface PublicSignatureContract {
            public suspend fun transform(
                block: (List<Parcelable>) -> Parcelable,
            ): List<Parcelable>
        }
        """.trimIndent(),
    )
    writeFile(
        "domain/station/src/main/kotlin/com/gasstation/domain/station/RequiredGenericContract.kt",
        """
        package com.gasstation.domain.station

        public interface RequiredGenericContract {
            public fun required(): List<String>
        }
        """.trimIndent(),
    )
    writeFile(
        "config/quality/public-api-signatures.txt",
        "schema-version=1\n" +
            ":domain:station|com/gasstation/domain/station/PublicSignatureContract|fun|transform|" +
            "(Lkotlin/jvm/functions/Function1;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;|" +
            "(Lkotlin/jvm/functions/Function1<-Ljava/util/List<+Landroid/os/Parcelable;>;" +
            "+Landroid/os/Parcelable;>;Lkotlin/coroutines/Continuation<" +
            "-Ljava/util/List<+Landroid/os/Parcelable;>;>;)Ljava/lang/Object;\n" +
            ":domain:station|com/gasstation/domain/station/RequiredGenericContract|fun|required|" +
                "()Ljava/util/List;|()Ljava/util/List<Ljava/lang/String;>;\n",
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
                if (module == ":feature:sample") {
                    appendLine("configurations.create(\"ksp\")")
                    appendLine("dependencies {")
                    appendLine("    compileOnly(project(\":core:database\"))")
                    appendLine("    compileOnlyApi(project(\":core:network\"))")
                    appendLine("    runtimeOnly(project(\":domain:sample\"))")
                    appendLine("    testImplementation(project(\":data:sample\"))")
                    appendLine("    add(\"ksp\", project(\":data:sample\"))")
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
                    val components = when {
                        consumer.startsWith(":benchmark") -> "benchmark,debug"
                        consumer == ":library" -> "demoDebug,demoRelease,prodDebug,prodRelease"
                        else -> "debug,release"
                    }
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
            scopes +=
                "scope|:domain:station|external|com.google.android:android|compileOnly|" +
                    "compile=main|runtime=-"
            if (mutation == TestedTargetMutation.VALID_FALSE) {
                scopes +=
                    "scope|:benchmark|project|:app|compileOnly|" +
                        "compile=benchmark,debug|runtime=-"
            } else if (mutation == TestedTargetMutation.ALL_INVALID) {
                scopes +=
                    "scope|:benchmark-valid-false|project|:app|compileOnly|" +
                        "compile=benchmark,debug|runtime=-"
                benchmarkMutations.forEach { (consumer, benchmarkMutation) ->
                    if (
                        benchmarkMutation !in
                            setOf(
                                TestedTargetMutation.COMPILE_ONLY_EXTRA,
                                TestedTargetMutation.COMPILE_ONLY_DUPLICATE,
                                TestedTargetMutation.COMPILE_ONLY_CHANGED,
                            )
                    ) return@forEach
                    val targets = when (benchmarkMutation) {
                        TestedTargetMutation.COMPILE_ONLY_EXTRA -> listOf(":app", ":core:model")
                        TestedTargetMutation.COMPILE_ONLY_CHANGED -> listOf(":other-app")
                        else -> listOf(":app")
                    }
                    targets.forEach { target ->
                        scopes +=
                            "scope|$consumer|project|$target|compileOnly|" +
                                "compile=benchmark,debug|runtime=-"
                    }
                }
            }
            scopes +=
                "scope|:graph:unresolved|external|invalid.example:never-resolve|implementation|" +
                    "compile=main|runtime=main"
            scopes += listOf(
                "scope|:feature:sample|project|:core:database|compileOnly|compile=main|runtime=-",
                "scope|:feature:sample|project|:core:network|compileOnlyApi|compile=main|runtime=-",
                "scope|:feature:sample|project|:domain:sample|runtimeOnly|compile=-|runtime=main",
                "scope|:library|project|:core:model|demoImplementation|compile=demoDebug,demoRelease|runtime=demoDebug,demoRelease",
                "scope|:library|project|:core:observability|debugImplementation|compile=demoDebug,prodDebug|runtime=demoDebug,prodDebug",
                "scope|:library|project|:domain:location|demoDebugImplementation|compile=demoDebug|runtime=demoDebug",
            )
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
                    "compile-only-membership=${if (expectedSelfInstrumenting) "absent" else "present"}|" +
                    "compile-only-identities=${
                        if (expectedSelfInstrumenting) {
                            "-"
                        } else {
                            "benchmark:compileOnly->:app@targetConfiguration=default," +
                                "debug:compileOnly->:app@targetConfiguration=default"
                        }
                    }",
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
