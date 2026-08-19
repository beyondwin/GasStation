package com.gasstation.buildlogic.quality

import com.gasstation.buildlogic.testing.GradlePluginTestProject
import com.gasstation.buildlogic.testing.RootQualityDependencyBucket
import com.gasstation.buildlogic.testing.RootQualityFixedInputMutation
import com.gasstation.buildlogic.testing.RootQualityFixture
import com.gasstation.buildlogic.testing.RootQualityProjectDependency
import com.gasstation.buildlogic.testing.assertConfigurationCacheReused
import com.gasstation.buildlogic.testing.assertConfigurationCacheStored
import com.gasstation.buildlogic.testing.assertTaskOutcome
import com.gasstation.buildlogic.testing.writeRootQualityFixture
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GasStationRootQualityConventionPluginTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val sharedGradleUserHome by lazy {
        temporaryFolder.newFolder("root-quality-gradle-user-home")
    }

    @Test
    fun rootPluginKeepsExactTaskSurfaceAndRunsEveryGuardWithoutAggregates() {
        val project = newProject("task-surface")
            .writeRootQualityFixture(RootQualityFixture(modules = emptyList()))

        val result =
            project.runner(
                "verifyModuleBoundaries",
                "verifyNoDeprecatedComposeTestApis",
                "verifyCiRobolectricRuntime",
                "--rerun-tasks",
            ).build()
        result.assertTaskOutcome(":verifyModuleBoundaries", TaskOutcome.SUCCESS)
        result.assertTaskOutcome(":verifyNoDeprecatedComposeTestApis", TaskOutcome.SUCCESS)
        result.assertTaskOutcome(":verifyCiRobolectricRuntime", TaskOutcome.SUCCESS)
        assertSuccessSentinels(result, expectedModuleCount = 0)

        val tasks = project.runner("tasks", "--group", "verification", "--all").build()
        assertTaskLine(
            tasks,
            "verifyModuleBoundaries",
            "docs/module-contracts.md 의 exact production dependency scope를 검증한다.",
        )
        assertTaskLine(
            tasks,
            "verifyNoDeprecatedComposeTestApis",
            "Fails when deprecated Compose v1 test-environment APIs are imported.",
        )
        assertTaskLine(
            tasks,
            "verifyCiRobolectricRuntime",
            "Fails when the CI Java runtime cannot execute the configured Robolectric SDK.",
        )
        listOf("quality", "rootQuality", "qualityGate", "check").forEach { absentTask ->
            assertFalse(
                "unexpected aggregate task $absentTask in ${tasks.output}",
                tasks.output.lineSequence().any { it.startsWith("$absentTask -") },
            )
        }
    }

    @Test
    fun subprojectApplicationFailsBeforeAnyGuardCanSucceed() {
        val project = newProject("subproject-application")
            .writeRootQualityFixture(
                RootQualityFixture(
                    modules = listOf(":sample"),
                    pluginProjectPath = ":sample",
                ),
            )

        val result = project.runner(":sample:tasks", "--all").buildAndFail()

        assertTrue(
            result.output.contains("gasstation.root.quality must be applied to the root project only"),
        )
        assertFalse(result.tasks.any { it.path.startsWith(":verify") && it.outcome == TaskOutcome.SUCCESS })
    }

    @Test
    fun consumingBuildCannotReplaceAnyFixedPolicyModelOrFileInput() {
        RootQualityFixedInputMutation.entries.forEach { mutation ->
            val project = newProject("immutable-${mutation.name.lowercase()}")
                .writeRootQualityFixture(
                    RootQualityFixture(
                        modules = listOf(":sample"),
                        fixedInputMutation = mutation,
                    ),
                )

            val taskName =
                when (mutation) {
                    RootQualityFixedInputMutation.REPLACE_MODULE_PATHS,
                    -> "verifyModuleBoundaries"
                    RootQualityFixedInputMutation.REDIRECT_WORKFLOW,
                    RootQualityFixedInputMutation.REDIRECT_ROBOLECTRIC_CONFIG,
                    -> "verifyCiRobolectricRuntime"
                    RootQualityFixedInputMutation.REPLACE_COMPOSE_SOURCES ->
                        "verifyNoDeprecatedComposeTestApis"
                }
            val result = project.runner(taskName).buildAndFail()

            assertTrue(
                "mutation $mutation did not hit an immutable property: ${result.output}",
                result.output.contains("cannot be changed") ||
                    result.output.contains("does not allow further changes"),
            )
            assertFalse(result.tasks.any { it.path.startsWith(":verify") && it.outcome == TaskOutcome.SUCCESS })
        }
    }

    @Test
    fun moduleGuardUsesSettingsProjectsAllowsExceptionAndTestOnlyEdgeWithoutResolving() {
        val modules =
            listOf(
                ":core:location",
                ":domain:location",
                ":feature:sample",
                ":data:sample",
                ":external:sample",
            )
        val project = newProject("module-allowed")
            .writeRootQualityFixture(
                RootQualityFixture(
                    modules = modules,
                    projectDependencies =
                        listOf(
                            RootQualityProjectDependency(
                                ":core:location",
                                RootQualityDependencyBucket.IMPLEMENTATION,
                                ":domain:location",
                            ),
                            RootQualityProjectDependency(
                                ":feature:sample",
                                RootQualityDependencyBucket.TEST_IMPLEMENTATION,
                                ":data:sample",
                            ),
                        ),
                    externalImplementation = "invalid.example:never-resolve:1.0",
                ),
            )
            .writeFile("filesystem-only/build.gradle.kts", "plugins { `java-library` }")
            .writeFile("filesystem-decoy-a/build.gradle.kts", "plugins { `java-library` }")
            .writeFile("filesystem-decoy-b/build.gradle.kts", "plugins { `java-library` }")
            .writeFile("filesystem-decoy-c/build.gradle.kts", "plugins { `java-library` }")

        val result = project.runner("verifyModuleBoundaries", "--rerun-tasks").build()

        result.assertTaskOutcome(":verifyModuleBoundaries", TaskOutcome.SUCCESS)
        assertTrue(result.output.contains("(10개 모듈 검사)"))
        assertFalse(result.output.contains("Could not resolve"))
        assertFalse(result.output.contains("filesystem-only"))
        assertFalse(result.output.contains("filesystem-decoy"))
    }

    @Test
    fun moduleGuardCapturesApiImplementationNestedProjectsAndSortedUniqueViolations() {
        val dependencies =
            listOf(
                RootQualityProjectDependency(":feature:sample", RootQualityDependencyBucket.IMPLEMENTATION, ":data:sample"),
                RootQualityProjectDependency(":feature:nested:sample", RootQualityDependencyBucket.IMPLEMENTATION, ":core:database"),
                RootQualityProjectDependency(":domain:sample", RootQualityDependencyBucket.API, ":core:network"),
                RootQualityProjectDependency(":feature:sample", RootQualityDependencyBucket.IMPLEMENTATION, ":data:sample"),
            )
        val project = newProject("module-violations")
            .writeRootQualityFixture(
                RootQualityFixture(
                    modules = dependencies.flatMap { listOf(it.consumer, it.target) }.distinct(),
                    projectDependencies = dependencies,
                    blockingDependencyPolicy = true,
                ),
            )

        val result = project.runner("verifyModuleBoundaries", "--rerun-tasks").buildAndFail()

        result.assertTaskOutcome(":verifyModuleBoundaries", TaskOutcome.FAILED)
        val expected =
            listOf(
                "unallowlisted direct production declaration: scope|:domain:sample|project|:core:network|api|compile=main|runtime=main",
                "unallowlisted direct production declaration: scope|:feature:nested:sample|project|:core:database|implementation|compile=main|runtime=main",
                "unallowlisted direct production declaration: scope|:feature:sample|project|:data:sample|implementation|compile=main|runtime=main",
            )
        expected.forEach { assertTrue(result.output.contains(it)) }
        val positions = expected.map(result.output::indexOf)
        assertTrue("violations not sorted: $positions", positions.zipWithNext().all { it.first < it.second })
        assertTrue(result.output.contains("production dependency policy violations 3"))
    }

    @Test
    fun failingModuleGuardReusesConfigurationCacheAndReproducesPolicyEvidence() {
        val project = newProject("cache-failure")
            .writeRootQualityFixture(
                RootQualityFixture(
                    modules = listOf(":feature:sample", ":data:sample"),
                    projectDependencies =
                        listOf(
                            RootQualityProjectDependency(
                                ":feature:sample",
                                RootQualityDependencyBucket.IMPLEMENTATION,
                                ":data:sample",
                            ),
                        ),
                    blockingDependencyPolicy = true,
                ),
            )
        val arguments = arrayOf("verifyModuleBoundaries", "--rerun-tasks")
        val expected =
            "unallowlisted direct production declaration: " +
                "scope|:feature:sample|project|:data:sample|implementation|compile=main|runtime=main"

        val first = project.configurationCacheRunner(*arguments).buildAndFail()
        first.assertTaskOutcome(":verifyModuleBoundaries", TaskOutcome.FAILED)
        first.assertConfigurationCacheStored()
        assertTrue(first.output.contains(expected))
        val firstReport = project.projectDir.resolve("build/reports/quality/module-boundaries.json").readBytes()

        val second = project.configurationCacheRunner(*arguments).buildAndFail()
        second.assertTaskOutcome(":verifyModuleBoundaries", TaskOutcome.FAILED)
        second.assertConfigurationCacheReused()
        assertTrue(second.output.contains(expected))
        assertArrayEquals(firstReport, project.projectDir.resolve("build/reports/quality/module-boundaries.json").readBytes())
    }

    @Test
    fun componentlessActiveModuleFailsClosed() {
        val project =
            newProject("componentless-active")
                .writeRootQualityFixture(
                    RootQualityFixture(
                        modules = listOf(":empty"),
                        componentlessModules = setOf(":empty"),
                    ),
                )

        val result = project.runner("verifyModuleBoundaries", "--rerun-tasks").buildAndFail()

        result.assertTaskOutcome(":verifyModuleBoundaries", TaskOutcome.FAILED)
        assertTrue(result.output.contains("active modules without production components: [:empty]"))
    }

    @Test
    fun composeGuardAllowsV2NormalCommentsAndExcludedTrees() {
        val project = newProject("compose-safe")
            .writeRootQualityFixture(
                RootQualityFixture(
                    modules = listOf(":feature:sample"),
                    kotlinSources =
                        mapOf(
                            "feature/sample/src/test/kotlin/fixture/Allowed.kt" to
                                """
                                package fixture
                                import androidx.compose.ui.test.ExperimentalTestApi
                                // import androidx.compose.ui.test.runComposeUiTest
                                class Allowed
                                """.trimIndent(),
                        ),
                ),
            )
            .writeFile(
                "feature/sample/build/generated/src/test/kotlin/Generated.kt",
                "import androidx.compose.ui.test.runComposeUiTest",
            )
            .writeFile(
                ".worktrees/other/src/test/kotlin/Other.kt",
                "import androidx.compose.ui.test.runComposeUiTest",
            )

        val result = project.runner("verifyNoDeprecatedComposeTestApis", "--rerun-tasks").build()

        result.assertTaskOutcome(":verifyNoDeprecatedComposeTestApis", TaskOutcome.SUCCESS)
        assertTrue(result.output.contains(COMPOSE_SUCCESS))
    }

    @Test
    fun composeGuardRejectsEveryDeprecatedPrefixFromRealUnitTestSources() {
        val sources =
            FORBIDDEN_IMPORTS.mapIndexed { index, forbiddenImport ->
                "feature/sample/src/test/kotlin/fixture/Deprecated${FORBIDDEN_IMPORTS.size - index}.kt" to
                    "package fixture\n$forbiddenImport\nclass Deprecated${index + 1}"
            }.toMap()
        val project = newProject("compose-eight-prefixes")
            .writeRootQualityFixture(
                RootQualityFixture(
                    modules = listOf(":feature:sample"),
                    kotlinSources = sources,
                ),
            )

        val result = project.runner("verifyNoDeprecatedComposeTestApis", "--rerun-tasks").buildAndFail()

        result.assertTaskOutcome(":verifyNoDeprecatedComposeTestApis", TaskOutcome.FAILED)
        FORBIDDEN_IMPORTS.forEach { forbiddenImport ->
            assertTrue(
                "missing diagnostic for $forbiddenImport",
                result.output.contains(forbiddenImport),
            )
        }
    }

    @Test
    fun composeGuardCoversAndroidTestAndReportsSortedRelativePathsAndLines() {
        val project = newProject("compose-relative")
            .writeRootQualityFixture(
                RootQualityFixture(
                    modules = listOf(":feature:sample"),
                    kotlinSources =
                        mapOf(
                            "feature/sample/src/test/kotlin/zeta/Last.kt" to
                                "package zeta\n\nimport androidx.compose.ui.test.runComposeUiTest",
                            "feature/sample/src/androidTest/kotlin/alpha/First.kt" to
                                "package alpha\nimport androidx.compose.ui.test.runAndroidComposeUiTest",
                        ),
                ),
            )

        val result = project.runner("verifyNoDeprecatedComposeTestApis", "--rerun-tasks").buildAndFail()

        result.assertTaskOutcome(":verifyNoDeprecatedComposeTestApis", TaskOutcome.FAILED)
        val first = "feature/sample/src/androidTest/kotlin/alpha/First.kt:2"
        val last = "feature/sample/src/test/kotlin/zeta/Last.kt:3"
        assertTrue(result.output.contains(first))
        assertTrue(result.output.contains(last))
        assertTrue(result.output.indexOf(first) < result.output.indexOf(last))
        assertFalse(result.output.contains(project.projectDir.absolutePath))
        assertFalse(result.output.contains('\\'))
    }

    @Test
    fun runtimeGuardKeepsSdkThresholdsAndValidatesEveryJavaDeclaration() {
        val project = newProject("runtime-boundaries")
            .writeRootQualityFixture(RootQualityFixture(modules = emptyList()))

        val java21Sdk36 = project.runner("verifyCiRobolectricRuntime", "--rerun-tasks").build()
        java21Sdk36.assertTaskOutcome(":verifyCiRobolectricRuntime", TaskOutcome.SUCCESS)
        assertTrue(java21Sdk36.output.contains("Java 21 supports test SDK 36"))

        project.writeFile(".github/workflows/android.yml", validWorkflow("17"))
            .writeFile("config/robolectric/robolectric.properties", "sdk=35")
        val java17Sdk35 = project.runner("verifyCiRobolectricRuntime", "--rerun-tasks").build()
        java17Sdk35.assertTaskOutcome(":verifyCiRobolectricRuntime", TaskOutcome.SUCCESS)

        project.writeFile("config/robolectric/robolectric.properties", "sdk=36")
        val java17Sdk36 = project.runner("verifyCiRobolectricRuntime", "--rerun-tasks").buildAndFail()
        java17Sdk36.assertTaskOutcome(":verifyCiRobolectricRuntime", TaskOutcome.FAILED)
        assertTrue(java17Sdk36.output.contains("Robolectric SDK 36 requires Java 21 or newer"))
        assertTrue(java17Sdk36.output.contains("Android CI declares Java 17"))

        listOf(
            listOf("\${{ env.CI_JAVA_VERSION }}", "17"),
            listOf("17", "\${{ env.CI_JAVA_VERSION }}"),
        ).forEach { expressions ->
            project.writeFile(".github/workflows/android.yml", workflow("21", expressions))
            val wrongDeclaration =
                project.runner("verifyCiRobolectricRuntime", "--rerun-tasks").buildAndFail()
            wrongDeclaration.assertTaskOutcome(":verifyCiRobolectricRuntime", TaskOutcome.FAILED)
            assertTrue(wrongDeclaration.output.contains("Every Android CI setup-java step must use"))
            assertTrue(wrongDeclaration.output.contains("17"))
        }

        project.writeFile(
            ".github/workflows/android.yml",
            workflow("21", listOf("\${{ env.CI_JAVA_VERSION }}", "\${{ env.CI_JAVA_VERSION }}")),
        )
        val everyCorrect = project.runner("verifyCiRobolectricRuntime", "--rerun-tasks").build()
        everyCorrect.assertTaskOutcome(":verifyCiRobolectricRuntime", TaskOutcome.SUCCESS)
    }

    @Test
    fun runtimeGuardFailsClosedForMissingAndNonNumericInputs() {
        val project = newProject("runtime-invalid")
            .writeRootQualityFixture(RootQualityFixture(modules = emptyList()))
        val invalidCases =
            listOf(
                Triple(workflowWithoutCiVersion(), "sdk=36", "must declare a top-level CI_JAVA_VERSION"),
                Triple(workflow("twenty-one"), "sdk=36", "must declare a top-level CI_JAVA_VERSION"),
                Triple(validWorkflow("21"), "name=value", "must declare a numeric sdk"),
                Triple(validWorkflow("21"), "sdk=thirty-six", "must declare a numeric sdk"),
                Triple(workflow("21", emptyList()), "sdk=36", "Every Android CI setup-java step must use"),
            )

        invalidCases.forEachIndexed { index, (workflow, config, diagnostic) ->
            project.writeFile(".github/workflows/android.yml", workflow)
                .writeFile("config/robolectric/robolectric.properties", config)
            val result = project.runner("verifyCiRobolectricRuntime", "--rerun-tasks").buildAndFail()
            result.assertTaskOutcome(":verifyCiRobolectricRuntime", TaskOutcome.FAILED)
            assertTrue("invalid case $index missing $diagnostic", result.output.contains(diagnostic))
        }
    }

    @Test
    fun allThreeGuardsStoreAndReuseConfigurationCacheWithNamedSuccessOutcomes() {
        val project = newProject("cache-success")
            .writeRootQualityFixture(
                RootQualityFixture(
                    modules =
                        listOf(
                            ":core:model",
                            ":core:observability",
                            ":domain:location",
                            ":domain:settings",
                            ":domain:station",
                        ),
                    contractApiFixture = true,
                ),
            )
        val arguments =
            arrayOf(
                "verifyModuleBoundaries",
                "verifyNoDeprecatedComposeTestApis",
                "verifyCiRobolectricRuntime",
                "verifyPublicApiBoundaries",
                "--rerun-tasks",
            )

        val first = project.configurationCacheRunner(*arguments).build()
        assertThreeSuccessOutcomes(first)
        assertContractApiOutcomes(first)
        first.assertConfigurationCacheStored()
        assertSuccessSentinels(first, expectedModuleCount = 7)
        val report = project.projectDir.resolve("build/reports/quality/public-api-boundaries.json")
        val firstReport = report.readBytes()
        assertTrue(firstReport.toString(Charsets.UTF_8).contains("\"selectedClassCount\":5"))

        val second = project.configurationCacheRunner(*arguments).build()
        assertThreeSuccessOutcomes(second)
        assertContractApiOutcomes(second)
        second.assertConfigurationCacheReused()
        assertSuccessSentinels(second, expectedModuleCount = 7)
        assertArrayEquals(firstReport, report.readBytes())

        project.writeFile(
            "core/model/api/unexpected.api",
            "public final class com/gasstation/core/model/Unexpected {\n\tpublic fun <init> ()V\n}\n",
        )
        val extraDump = project.configurationCacheRunner(*arguments).buildAndFail()
        extraDump.assertTaskOutcome(":verifyPublicApiBoundaries", TaskOutcome.FAILED)
        extraDump.assertConfigurationCacheReused()
        assertTrue(extraDump.output.contains("unexpected or ambiguous ABI dump: unexpected.api"))
        assertFalse(extraDump.tasks.any { it.path.endsWith(":updateKotlinAbi") })
    }

    @Test
    fun equivalentFixturesRelocateWithoutAbsoluteSuccessOrFailureEvidence() {
        listOf("relocation-a", "relocation-b").forEach { name ->
            val project = newProject(name)
                .writeRootQualityFixture(RootQualityFixture(modules = listOf(":sample")))
            val result = project.runner("verifyModuleBoundaries", "--rerun-tasks").build()
            result.assertTaskOutcome(":verifyModuleBoundaries", TaskOutcome.SUCCESS)
            assertFalse(result.output.contains(project.projectDir.absolutePath))
            assertFalse(
                project.projectDir.resolve("build/reports/quality/module-boundaries.json")
                    .readText().contains(project.projectDir.absolutePath),
            )
        }
    }

    private fun newProject(name: String): GradlePluginTestProject =
        GradlePluginTestProject.create(
            temporaryFolder.newFolder("$name-root"),
            sharedGradleUserHome,
        )

    private fun assertTaskLine(result: BuildResult, task: String, description: String) {
        assertTrue(
            "missing exact task surface for $task: ${result.output}",
            result.output.lineSequence().any { it == "$task - $description" },
        )
    }

    private fun assertThreeSuccessOutcomes(result: BuildResult) {
        result.assertTaskOutcome(":verifyModuleBoundaries", TaskOutcome.SUCCESS)
        result.assertTaskOutcome(":verifyNoDeprecatedComposeTestApis", TaskOutcome.SUCCESS)
        result.assertTaskOutcome(":verifyCiRobolectricRuntime", TaskOutcome.SUCCESS)
    }

    private fun assertContractApiOutcomes(result: BuildResult) {
        listOf(
            ":core:model",
            ":core:observability",
            ":domain:location",
            ":domain:settings",
            ":domain:station",
        ).forEach { module ->
            result.assertTaskOutcome("$module:checkKotlinAbi", TaskOutcome.SUCCESS)
        }
        result.assertTaskOutcome(":verifyPublicApiBoundaries", TaskOutcome.SUCCESS)
        assertFalse(result.tasks.any { it.path.endsWith(":updateKotlinAbi") })
    }

    private fun assertSuccessSentinels(result: BuildResult, expectedModuleCount: Int) {
        assertTrue(result.output.contains("(${expectedModuleCount}개 모듈 검사)"))
        assertTrue(result.output.contains(COMPOSE_SUCCESS))
        assertTrue(result.output.contains("CI/Robolectric runtime OK"))
    }

    private fun validWorkflow(javaVersion: String): String = workflow(javaVersion)

    private fun workflow(
        javaVersion: String,
        expressions: List<String> = listOf("\${{ env.CI_JAVA_VERSION }}"),
    ): String =
        buildString {
            appendLine("name: Android CI")
            appendLine("env:")
            appendLine("  CI_JAVA_VERSION: \"$javaVersion\"")
            appendLine("jobs:")
            appendLine("  test:")
            appendLine("    steps:")
            expressions.forEach { expression ->
                appendLine("      - uses: actions/setup-java@v5")
                appendLine("        with:")
                appendLine("          java-version: $expression")
            }
        }

    private fun workflowWithoutCiVersion(): String =
        """
        name: Android CI
        jobs:
          test:
            steps:
              - uses: actions/setup-java@v5
                with:
                  java-version: ${'$'}{{ env.CI_JAVA_VERSION }}
        """.trimIndent()

    companion object {
        private const val COMPOSE_SUCCESS =
            "Compose test API guard OK: deprecated v1 test-environment imports not found."

        private val FORBIDDEN_IMPORTS =
            listOf(
                "import androidx.compose.ui.test.junit4.AndroidComposeTestRule",
                "import androidx.compose.ui.test.junit4.createAndroidComposeRule",
                "import androidx.compose.ui.test.junit4.createComposeRule",
                "import androidx.compose.ui.test.junit4.createEmptyComposeRule",
                "import androidx.compose.ui.test.AndroidComposeUiTestEnvironment",
                "import androidx.compose.ui.test.runAndroidComposeUiTest",
                "import androidx.compose.ui.test.runComposeUiTest",
                "import androidx.compose.ui.test.runEmptyComposeUiTest",
            )
    }
}
