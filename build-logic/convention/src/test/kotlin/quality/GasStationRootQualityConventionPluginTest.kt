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
    fun runtimeGuardRequiresExactClosedInstallerRolesAndSdkThreshold() {
        val project = newProject("runtime-boundaries")
            .writeRootQualityFixture(RootQualityFixture(modules = emptyList()))

        val java21Sdk36 = project.runner("verifyCiRobolectricRuntime", "--rerun-tasks").build()
        java21Sdk36.assertTaskOutcome(":verifyCiRobolectricRuntime", TaskOutcome.SUCCESS)
        assertTrue(java21Sdk36.output.contains("Java 21 supports test SDK 36"))

        listOf(
            workflow(runtime = "21.0.12+8") to "exact CI_JAVA_VERSION",
            workflow(toolchain = "17") to "exact CI_JAVA_TOOLCHAIN_VERSION",
            workflow(extraStep = "      - uses: actions/setup-java@v5") to "may not use actions/setup-java",
            workflow(includeInstaller = false) to "must use ./.github/actions/setup-build-inputs",
            workflow(runner = "ubuntu-latest") to "ubuntu-24.04",
        ).forEachIndexed { index, (candidate, diagnostic) ->
            project.writeFile(".github/workflows/android.yml", candidate)
            val result = project.runner("verifyCiRobolectricRuntime", "--rerun-tasks").buildAndFail()
            result.assertTaskOutcome(":verifyCiRobolectricRuntime", TaskOutcome.FAILED)
            assertTrue("runtime mutation $index missing $diagnostic", result.output.contains(diagnostic))
        }
    }

    @Test
    fun runtimeGuardFailsClosedForMissingAndNonNumericInputs() {
        val project = newProject("runtime-invalid")
            .writeRootQualityFixture(RootQualityFixture(modules = emptyList()))
        val invalidCases =
            listOf(
                Triple(workflowWithoutCiVersion(), "sdk=36", "exact CI_JAVA_VERSION"),
                Triple(workflow(runtime = "twenty-one"), "sdk=36", "exact CI_JAVA_VERSION"),
                Triple(workflow(), "name=value", "must declare a numeric sdk"),
                Triple(workflow(), "sdk=thirty-six", "must declare a numeric sdk"),
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
                    mutationFixture = true,
                ),
            )
        val arguments =
            arrayOf(
                "verifyModuleBoundaries",
                "verifyNoDeprecatedComposeTestApis",
                "verifyCiRobolectricRuntime",
                "verifyPublicApiBoundaries",
                "verifyPitestConfiguration",
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
        val mutationConfiguration =
            listOf("location", "settings", "station").associateWith { module ->
                val path = project.projectDir.resolve("domain/$module/build/reports/quality/pitest-configuration.json")
                assertTrue("missing mutation configuration for $module", path.isFile)
                val bytes = path.readBytes()
                val text = bytes.toString(Charsets.UTF_8)
                assertTrue(text, text.contains("\"hostNeutralMutationIdentity\""))
                assertTrue(text, text.contains("\"perRunExecutionProvenance\""))
                assertFalse(text, text.contains(project.projectDir.absolutePath))
                bytes
            }

        val second = project.configurationCacheRunner(*arguments).build()
        assertThreeSuccessOutcomes(second)
        assertContractApiOutcomes(second)
        second.assertConfigurationCacheReused()
        assertSuccessSentinels(second, expectedModuleCount = 7)
        assertArrayEquals(firstReport, report.readBytes())
        mutationConfiguration.forEach { (module, bytes) ->
            assertArrayEquals(
                bytes,
                project.projectDir.resolve("domain/$module/build/reports/quality/pitest-configuration.json").readBytes(),
            )
        }

    }

    @Test
    fun abiUpdaterRequiresExactOperatorVectorAndRejectsVerificationGraphBeforeDumpWrites() {
        val project = newProject("abi-update-operator")
            .writeRootQualityFixture(
                RootQualityFixture(
                    modules = CONTRACT_API_MODULES,
                    contractApiFixture = true,
                ),
            )
        val dumpPaths =
            listOf(
                "core/model/api/model.api",
                "core/observability/api/observability.api",
                "domain/location/api/location.api",
                "domain/settings/api/settings.api",
                "domain/station/api/station.api",
            )
        val exactOperatorVector =
            listOf(
                ":core:model:updateKotlinAbi",
                ":core:observability:updateKotlinAbi",
                ":domain:location:updateKotlinAbi",
                ":domain:settings:updateKotlinAbi",
                ":domain:station:updateKotlinAbi",
            )
        val initialDumpBytes = dumpPaths.associateWith { project.projectDir.resolve(it).readBytes() }
        val invalidRequests =
            listOf(
                "partial" to listOf(":core:model:updateKotlinAbi"),
                "mixed" to listOf(":core:model:updateKotlinAbi", "verifyPublicApiBoundaries"),
                "duplicate" to exactOperatorVector + ":domain:station:updateKotlinAbi",
                "reordered" to exactOperatorVector.reversed(),
                "extra" to exactOperatorVector + "verifyModuleBoundaries",
                "shorthand" to listOf("updateKotlinAbi"),
                "other-updater" to listOf(":core:network:updateKotlinAbi"),
            )

        invalidRequests.forEach { (name, requestedTasks) ->
            val result =
                project.runner(*requestedTasks.toTypedArray())
                    .buildAndFail()

            assertTrue(
                "$name request did not hit the exact operator-vector guard: ${result.output}",
                result.output.contains("updateKotlinAbi operator protocol violation") &&
                    result.output.contains("exact requested task vector"),
            )
            assertFalse(
                result.tasks.any {
                    it.path.endsWith(":updateKotlinAbi") && it.outcome == TaskOutcome.SUCCESS
                },
            )
            dumpPaths.forEach { path ->
                assertArrayEquals(
                    "$name request changed $path",
                    initialDumpBytes.getValue(path),
                    project.projectDir.resolve(path).readBytes(),
                )
            }
        }

        val staleBytes = "stale fixture ABI\n".toByteArray()
        dumpPaths.forEach { path -> project.projectDir.resolve(path).writeBytes(staleBytes) }
        project.projectDir.resolve(".fixture-allow-abi-update").writeText("fixture-only\n")
        val exact =
            project.runner(
                *(exactOperatorVector + "--rerun-tasks").toTypedArray(),
            ).build()

        exactOperatorVector.forEach { path -> exact.assertTaskOutcome(path, TaskOutcome.SUCCESS) }
        assertFalse(exact.tasks.any { it.path.endsWith(":checkKotlinAbi") })
        assertFalse(
            exact.tasks.any {
                it.path in CONTRACT_API_MODULES.map { module -> "$module:check" }
            },
        )
        dumpPaths.forEach { path ->
            assertFalse(
                "exact operator command did not update $path",
                project.projectDir.resolve(path).readBytes().contentEquals(staleBytes),
            )
        }

        assertTrue(project.projectDir.resolve(".fixture-allow-abi-update").delete())
        val reviewedDumpBytes = dumpPaths.associateWith { project.projectDir.resolve(it).readBytes() }
        project.projectDir.resolve("build.gradle.kts").appendText(
            """

            tasks.register("verifyFixtureEvidence") { group = "verification" }
            gradle.projectsEvaluated {
                project(":core:model").tasks.named("updateKotlinAbi") {
                    dependsOn(project(":core:model").tasks.named("check"))
                    dependsOn(rootProject.tasks.named("verifyModuleBoundaries"))
                    dependsOn(rootProject.tasks.named("verifyPublicApiBoundaries"))
                    dependsOn(rootProject.tasks.named("verifyCoverageReport"))
                    dependsOn(rootProject.tasks.named("verifyFixtureEvidence"))
                }
            }
            """.trimIndent(),
        )
        val forbiddenGraph =
            project.runner(*exactOperatorVector.toTypedArray())
                .buildAndFail()

        assertTrue(
            forbiddenGraph.output,
            forbiddenGraph.output.contains("updateKotlinAbi operator protocol violation"),
        )
        assertTrue(
            forbiddenGraph.output,
            forbiddenGraph.output.contains("verification tasks are forbidden in the update graph"),
        )
        listOf(
            ":core:model:check",
            ":core:model:checkKotlinAbi",
            ":verifyCoverageReport",
            ":verifyFixtureEvidence",
            ":verifyModuleBoundaries",
            ":verifyPublicApiBoundaries",
        ).forEach { path -> assertTrue(forbiddenGraph.output, forbiddenGraph.output.contains(path)) }
        dumpPaths.forEach { path ->
            assertArrayEquals(
                "forbidden graph changed $path",
                reviewedDumpBytes.getValue(path),
                project.projectDir.resolve(path).readBytes(),
            )
        }
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

    private fun workflow(
        runtime: String = "21.0.12.1+1",
        toolchain: String = "17.0.20+8",
        runner: String = "ubuntu-24.04",
        includeInstaller: Boolean = true,
        extraStep: String = "",
    ): String =
        buildString {
            appendLine("name: Android CI")
            appendLine("env:")
            appendLine("  CI_JAVA_TOOLCHAIN_VERSION: \"$toolchain\"")
            appendLine("  CI_JAVA_VERSION: \"$runtime\"")
            appendLine("jobs:")
            appendLine("  test:")
            appendLine("    runs-on: $runner")
            appendLine("    steps:")
            if (includeInstaller) appendLine("      - uses: ./.github/actions/setup-build-inputs")
            if (extraStep.isNotEmpty()) appendLine(extraStep)
        }

    private fun workflowWithoutCiVersion(): String =
        """
        name: Android CI
        env:
          CI_JAVA_TOOLCHAIN_VERSION: "17.0.20+8"
        jobs:
          test:
            runs-on: ubuntu-24.04
            steps:
              - uses: ./.github/actions/setup-build-inputs
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

        private val CONTRACT_API_MODULES =
            listOf(
                ":core:model",
                ":core:observability",
                ":domain:location",
                ":domain:settings",
                ":domain:station",
            )
    }
}
