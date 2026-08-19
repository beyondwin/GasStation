package com.gasstation.buildlogic.quality

import com.gasstation.buildlogic.testing.GradlePluginTestProject
import com.gasstation.buildlogic.testing.TestedTargetMutation
import com.gasstation.buildlogic.testing.assertConfigurationCacheReused
import com.gasstation.buildlogic.testing.assertConfigurationCacheStored
import com.gasstation.buildlogic.testing.assertTaskOutcome
import com.gasstation.buildlogic.testing.writeProductionDependencyAndroidFixture
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProductionDependencyBoundaryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val sharedGradleUserHome by lazy {
        temporaryFolder.newFolder("production-dependency-gradle-user-home")
    }

    @Test
    fun realAgpAndResolvedGraphContractsAreCoveredByOneMultiProjectBuild() {
        val project = newProject("tested-target-all-invalid")
            .writeProductionDependencyAndroidFixture(TestedTargetMutation.ALL_INVALID)

        val arguments =
            arrayOf(
                "verifyModuleBoundaries",
                "productionDependencyInventory",
                ":core:model:checkKotlinAbi",
                ":core:observability:checkKotlinAbi",
                ":domain:location:checkKotlinAbi",
                ":domain:settings:checkKotlinAbi",
                ":domain:station:checkKotlinAbi",
                "verifyPublicApiBoundaries",
                "--continue",
                "--rerun-tasks",
            )
        val result = project.configurationCacheRunner(*arguments).buildAndFail()

        result.assertTaskOutcome(":verifyModuleBoundaries", TaskOutcome.FAILED)
        result.assertTaskOutcome(":productionDependencyInventory", TaskOutcome.FAILED)
        result.assertTaskOutcome(":verifyPublicApiBoundaries", TaskOutcome.SUCCESS)
        assertContractAbiOutcomes(result)
        result.assertConfigurationCacheStored()
        assertTrue(result.output, result.output.contains("tested-target relation mismatch"))
        assertTrue(result.output, result.output.contains("tested-target-observation|:benchmark-invalid"))
        assertTrue(result.output, result.output.contains("targets=:app,:app,:core:model,:other-app"))
        assertTrue(result.output, result.output.contains("targets=-"))
        assertTrue(result.output, result.output.contains("expected=tested-target|:benchmark-invalid|benchmark,debug|:app"))
        assertTrue(result.output.contains("unresolved production dependency graph entries"))
        val boundaryReport = project.projectDir.resolve("build/reports/quality/module-boundaries.json").readText()
        assertTrue(boundaryReport.contains(":app|debug"))
        assertTrue(boundaryReport.contains(":library|release"))
        assertTrue(boundaryReport.contains(":benchmark-valid-true|benchmark"))
        assertTrue(
            boundaryReport.contains(
                "tested-target|:benchmark-valid-true|benchmark,debug|:app|" +
                    "self-instrumenting=true|compile-only-membership=absent",
            ),
        )
        assertTrue(
            boundaryReport.contains(
                "tested-target|:benchmark-valid-false|benchmark,debug|:app|" +
                    "self-instrumenting=false|compile-only-membership=present",
            ),
        )
        val report = project.projectDir.resolve("build/reports/quality/production-dependency-graph.json").readText()
        assertTrue(report.contains("|root|root=project:"))
        assertTrue(report.contains("path=project::domain:station>project::core:observability"))
        assertTrue(report.contains("path=project::domain:station>project::domain:location>project::core:observability"))
        assertTrue(report.contains("path=project::domain:station>project::domain:settings>project::core:observability"))
        assertTrue(report.contains("path=project::graph:a>project::graph:b>project::graph:a"))
        assertTrue(report.contains("|unresolved|root=project:"))
        assertTrue(report.contains("parent=project::graph:unresolved"))
        assertTrue(report.contains("requested=invalid.example:never-resolve:1.0"))
        assertTrue(report.contains("path=project::graph:unresolved>unresolved:invalid.example:never-resolve:1.0"))

        val firstBoundaryReport = boundaryReport.toByteArray()
        val firstInventoryReport = report.toByteArray()
        project.writeFile(
            "core/model/api/unexpected.api",
            "public final class com/gasstation/core/model/Unexpected {\n\tpublic fun <init> ()V\n}\n",
        )
        val replay = project.configurationCacheRunner(*arguments).buildAndFail()
        replay.assertConfigurationCacheReused()
        replay.assertTaskOutcome(":verifyModuleBoundaries", TaskOutcome.FAILED)
        replay.assertTaskOutcome(":productionDependencyInventory", TaskOutcome.FAILED)
        replay.assertTaskOutcome(":verifyPublicApiBoundaries", TaskOutcome.FAILED)
        assertContractAbiOutcomes(replay)
        assertTrue(replay.output.contains("unexpected or ambiguous ABI dump: unexpected.api"))
        assertFalse(replay.tasks.any { it.path.endsWith(":updateKotlinAbi") })
        assertArrayEquals(
            firstBoundaryReport,
            project.projectDir.resolve("build/reports/quality/module-boundaries.json").readBytes(),
        )
        assertArrayEquals(
            firstInventoryReport,
            project.projectDir.resolve("build/reports/quality/production-dependency-graph.json").readBytes(),
        )
    }

    private fun assertContractAbiOutcomes(result: org.gradle.testkit.runner.BuildResult) {
        listOf(":core:model", ":core:observability", ":domain:location", ":domain:settings", ":domain:station")
            .forEach { module -> result.assertTaskOutcome("$module:checkKotlinAbi", TaskOutcome.SUCCESS) }
    }

    private fun newProject(name: String): GradlePluginTestProject =
        GradlePluginTestProject.create(
            temporaryFolder.newFolder("$name-root"),
            sharedGradleUserHome,
        )
}
