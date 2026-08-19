package com.gasstation.buildlogic.testing

enum class RootQualityDependencyBucket(val notation: String) {
    API("api"),
    IMPLEMENTATION("implementation"),
    TEST_IMPLEMENTATION("testImplementation"),
}

data class RootQualityProjectDependency(
    val consumer: String,
    val bucket: RootQualityDependencyBucket,
    val target: String,
)

enum class RootQualityFixedInputMutation {
    CLEAR_FORBIDDEN_EDGES,
    REPLACE_MODULE_EDGES,
    REPLACE_MODULE_PATHS,
    REDIRECT_WORKFLOW,
    REDIRECT_ROBOLECTRIC_CONFIG,
    REPLACE_COMPOSE_SOURCES,
}

data class RootQualityFixture(
    val modules: List<String>,
    val projectDependencies: List<RootQualityProjectDependency> = emptyList(),
    val externalImplementation: String? = null,
    val ciJavaVersion: String = "21",
    val setupJavaExpressions: List<String> = listOf("\${{ env.CI_JAVA_VERSION }}"),
    val robolectricSdk: String = "36",
    val kotlinSources: Map<String, String> = emptyMap(),
    val pluginProjectPath: String = ":",
    val fixedInputMutation: RootQualityFixedInputMutation? = null,
    val contractApiFixture: Boolean = false,
)

fun GradlePluginTestProject.writeRootQualityFixture(
    fixture: RootQualityFixture,
): GradlePluginTestProject {
    val modules = fixture.modules.sorted()
    require(modules.toSet().size == modules.size) { "Fixture module paths must be unique" }
    modules.forEach(::requireRootQualityProjectPath)
    require(fixture.pluginProjectPath == ":" || fixture.pluginProjectPath in modules) {
        "Plugin project must be the root or an included module: ${fixture.pluginProjectPath}"
    }
    fixture.projectDependencies.forEach { dependency ->
        requireRootQualityProjectPath(dependency.consumer)
        requireRootQualityProjectPath(dependency.target)
        require(dependency.consumer in modules) {
            "Dependency consumer must be included: ${dependency.consumer}"
        }
        require(dependency.target in modules) {
            "Dependency target must be included: ${dependency.target}"
        }
    }
    fixture.kotlinSources.keys.forEach { relativePath ->
        require(
            relativePath.contains("/src/test/") || relativePath.contains("/src/androidTest/"),
        ) { "Kotlin fixture source must be a test source: $relativePath" }
        require(relativePath.endsWith(".kt")) { "Kotlin fixture source must end in .kt: $relativePath" }
    }

    writeSettings(
        buildString {
            if (fixture.contractApiFixture) {
                appendLine("dependencyResolutionManagement { repositories { mavenCentral() } }")
            }
            appendLine("rootProject.name = \"gasstation-root-quality-fixture\"")
            appendLine("val activeModulePaths: List<String> = listOf(${modules.joinToString { "\"$it\"" }})")
            appendLine("include(*activeModulePaths.toTypedArray())")
            appendLine("gradle.extensions.extraProperties.set(\"gasstation.activeModulePaths\", activeModulePaths.toList())")
        },
    )
    writeBuildFile(
        if (fixture.pluginProjectPath == ":") {
            rootQualityBuildScript(fixture.fixedInputMutation)
        } else {
            ""
        },
    )
    if (fixture.contractApiFixture) {
        require(modules == CONTRACT_API_MODULES.map(ContractApiFixture::module).sorted()) {
            "Contract API fixture requires the exact five ABI modules"
        }
        writeFile(
            "gradle/libs.versions.toml",
            """
            [versions]
            kotlin = "2.4.10"

            [libraries]
            kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin" }
            """.trimIndent(),
        )
    }

    val dependenciesByConsumer = fixture.projectDependencies.groupBy { it.consumer }
    modules.forEach { module ->
        val dependencies = dependenciesByConsumer[module].orEmpty()
        val externalImplementation =
            fixture.externalImplementation?.takeIf { module == modules.firstOrNull() }
        val buildScript =
            buildString {
                appendLine("plugins {")
                if (fixture.contractApiFixture) {
                    appendLine("    id(\"gasstation.jvm.library\")")
                } else {
                    appendLine("    `java-library`")
                }
                if (fixture.pluginProjectPath == module) {
                    appendLine("    id(\"gasstation.root.quality\")")
                }
                appendLine("}")
                if (dependencies.isNotEmpty() || externalImplementation != null) {
                    appendLine()
                    appendLine("dependencies {")
                    dependencies
                        .sortedWith(
                            compareBy<RootQualityProjectDependency>(
                                { it.bucket.notation },
                                { it.target },
                            ),
                        )
                        .forEach { dependency ->
                            appendLine(
                                "    ${dependency.bucket.notation}(project(\"${dependency.target}\"))",
                            )
                        }
                    externalImplementation?.let { coordinate ->
                        appendLine("    implementation(\"$coordinate\")")
                    }
                    appendLine("}")
                }
                if (fixture.contractApiFixture && module == ":core:model") {
                    appendLine()
                    appendLine(
                        "dependencies { compileOnly(\"org.jetbrains.kotlin:kotlin-stdlib:2.4.10\") }",
                    )
                }
            }
        writeFile(module.toFixtureDirectory().resolveFixture("build.gradle.kts"), buildScript)
    }

    if (fixture.contractApiFixture) {
        CONTRACT_API_MODULES.forEach { contract ->
            val directory = contract.module.toFixtureDirectory()
            val internalName = contract.packageName.replace('.', '/') + "/Marker"
            writeFile(
                directory.resolveFixture(
                    "src/main/kotlin/${contract.packageName.replace('.', '/')}/Marker.kt",
                ),
                "package ${contract.packageName}\n\npublic class Marker",
            )
            writeFile(
                contract.dumpPath,
                "public final class $internalName {\n\tpublic fun <init> ()V\n}\n",
            )
            projectDir.resolve(contract.dumpPath).appendText("\n")
        }
    }

    writeFile("src/test/kotlin/fixture/Safe.kt", SAFE_KOTLIN_SOURCE)
    fixture.kotlinSources.toSortedMap().forEach { (relativePath, source) ->
        writeFile(relativePath, source)
    }
    writeFile(".github/workflows/android.yml", workflow(fixture))
    writeFile(
        "config/robolectric/robolectric.properties",
        "sdk=${fixture.robolectricSdk}",
    )
    writeFile(
        "config/quality/production-dependency-policy.txt",
        buildString {
            appendLine("schema-version=1")
            appendLine("enforcement=report-only")
            modules.forEach { appendLine("module|$it") }
            if (fixture.contractApiFixture) {
                appendLine(
                    "scope|:core:model|external|org.jetbrains.kotlin:kotlin-stdlib|compileOnly|" +
                        "compile=main|runtime=-",
                )
            }
        },
    )
    return this
}

private fun rootQualityBuildScript(mutation: RootQualityFixedInputMutation?): String =
    buildString {
        appendLine("import com.gasstation.buildlogic.quality.VerifyCiRobolectricRuntimeTask")
        appendLine("import com.gasstation.buildlogic.quality.VerifyModuleBoundariesTask")
        appendLine("import com.gasstation.buildlogic.quality.VerifyNoDeprecatedComposeTestApisTask")
        appendLine()
        appendLine("plugins {")
        appendLine("    id(\"gasstation.root.quality\")")
        appendLine("}")
        when (mutation) {
            RootQualityFixedInputMutation.CLEAR_FORBIDDEN_EDGES -> {
                appendLine()
                appendLine("tasks.named<VerifyModuleBoundariesTask>(\"verifyModuleBoundaries\") {")
                appendLine("    forbiddenEdges.set(emptyList())")
                appendLine("}")
            }
            RootQualityFixedInputMutation.REPLACE_MODULE_EDGES -> {
                appendLine()
                appendLine("tasks.named<VerifyModuleBoundariesTask>(\"verifyModuleBoundaries\") {")
                appendLine("    moduleEdges.set(emptyList())")
                appendLine("}")
            }
            RootQualityFixedInputMutation.REPLACE_MODULE_PATHS -> {
                appendLine()
                appendLine("tasks.named<VerifyModuleBoundariesTask>(\"verifyModuleBoundaries\") {")
                appendLine("    modulePaths.set(emptyList())")
                appendLine("}")
            }
            RootQualityFixedInputMutation.REDIRECT_WORKFLOW -> {
                appendLine()
                appendLine("tasks.named<VerifyCiRobolectricRuntimeTask>(\"verifyCiRobolectricRuntime\") {")
                appendLine("    workflowFile.set(layout.projectDirectory.file(\"alternate.yml\"))")
                appendLine("}")
            }
            RootQualityFixedInputMutation.REDIRECT_ROBOLECTRIC_CONFIG -> {
                appendLine()
                appendLine("tasks.named<VerifyCiRobolectricRuntimeTask>(\"verifyCiRobolectricRuntime\") {")
                appendLine("    robolectricConfigFile.set(layout.projectDirectory.file(\"alternate.properties\"))")
                appendLine("}")
            }
            RootQualityFixedInputMutation.REPLACE_COMPOSE_SOURCES -> {
                appendLine()
                appendLine("tasks.named<VerifyNoDeprecatedComposeTestApisTask>(\"verifyNoDeprecatedComposeTestApis\") {")
                appendLine("    sources.setFrom(layout.projectDirectory.file(\"alternate.kt\"))")
                appendLine("}")
            }
            null -> Unit
        }
    }

private fun workflow(fixture: RootQualityFixture): String =
    buildString {
        appendLine("name: Android CI")
        appendLine("env:")
        appendLine("  CI_JAVA_VERSION: \"${fixture.ciJavaVersion}\"")
        appendLine("jobs:")
        appendLine("  test:")
        appendLine("    runs-on: ubuntu-latest")
        appendLine("    steps:")
        fixture.setupJavaExpressions.forEachIndexed { index, expression ->
            appendLine("      - name: Setup Java ${index + 1}")
            appendLine("        uses: actions/setup-java@v5")
            appendLine("        with:")
            appendLine("          distribution: temurin")
            appendLine("          java-version: $expression")
        }
    }

private fun requireRootQualityProjectPath(projectPath: String) {
    require(projectPath.matches(ROOT_QUALITY_PROJECT_PATH)) {
        "Fixture project path must be lowercase and absolute: $projectPath"
    }
}

private fun String.toFixtureDirectory(): String = removePrefix(":").replace(':', '/')

private fun String.resolveFixture(relativePath: String): String = "$this/$relativePath"

private val ROOT_QUALITY_PROJECT_PATH = Regex("(?::[a-z][a-z0-9-]*)+")

private data class ContractApiFixture(
    val module: String,
    val dumpPath: String,
    val packageName: String,
)

private val CONTRACT_API_MODULES =
    listOf(
        ContractApiFixture(":core:model", "core/model/api/model.api", "com.gasstation.core.model"),
        ContractApiFixture(
            ":core:observability",
            "core/observability/api/observability.api",
            "com.gasstation.core.observability",
        ),
        ContractApiFixture(
            ":domain:location",
            "domain/location/api/location.api",
            "com.gasstation.domain.location",
        ),
        ContractApiFixture(
            ":domain:settings",
            "domain/settings/api/settings.api",
            "com.gasstation.domain.settings",
        ),
        ContractApiFixture(
            ":domain:station",
            "domain/station/api/station.api",
            "com.gasstation.domain.station",
        ),
    )

private val SAFE_KOTLIN_SOURCE =
    """
    package fixture

    class Safe
    """.trimIndent()
