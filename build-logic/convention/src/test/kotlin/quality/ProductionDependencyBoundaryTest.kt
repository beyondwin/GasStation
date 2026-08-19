package com.gasstation.buildlogic.quality

import com.gasstation.buildlogic.testing.GradlePluginTestProject
import com.gasstation.buildlogic.testing.TestedTargetMutation
import com.gasstation.buildlogic.testing.assertTaskOutcome
import com.gasstation.buildlogic.testing.writeProductionDependencyAndroidFixture
import org.gradle.testkit.runner.TaskOutcome
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
    fun realAgpTestedApksRejectsMissingDuplicateExtraAndChangedTargetsTogether() {
        val project = newProject("tested-target-all-invalid")
            .writeProductionDependencyAndroidFixture(TestedTargetMutation.ALL_INVALID)

        val result = project.runner("verifyModuleBoundaries", "--rerun-tasks").buildAndFail()

        result.assertTaskOutcome(":verifyModuleBoundaries", TaskOutcome.FAILED)
        assertTrue(result.output, result.output.contains("tested-target relation mismatch"))
        assertTrue(result.output, result.output.contains("tested-target-observation|:benchmark-invalid"))
        assertTrue(result.output, result.output.contains("targets=:app,:app,:core:model,:other-app"))
        assertTrue(result.output, result.output.contains("targets=-"))
        assertTrue(result.output, result.output.contains("expected=tested-target|:benchmark-invalid|benchmark,debug|:app"))
    }

    @Test
    fun realAgpComponentsAndBothSelfInstrumentingModesUseObservedMembership() {
        listOf(TestedTargetMutation.VALID_TRUE, TestedTargetMutation.VALID_FALSE).forEach { mutation ->
            val project = newProject("tested-target-${mutation.name.lowercase()}")
                .writeProductionDependencyAndroidFixture(mutation)

            val tasks = buildList {
                add("verifyModuleBoundaries")
                if (mutation == TestedTargetMutation.VALID_TRUE) add("productionDependencyInventory")
                add("--rerun-tasks")
            }
            val result = project.runner(*tasks.toTypedArray()).build()

            result.assertTaskOutcome(":verifyModuleBoundaries", TaskOutcome.SUCCESS)
            val report = project.projectDir.resolve("build/reports/quality/module-boundaries.json").readText()
            assertTrue(report.contains(":app|debug"))
            assertTrue(report.contains(":library|release"))
            assertTrue(report.contains(":benchmark|benchmark"))
            assertTrue(report.contains("compile-only-membership=${if (mutation == TestedTargetMutation.VALID_TRUE) "absent" else "present"}"))
            if (mutation == TestedTargetMutation.VALID_TRUE) {
                val inventory = project.projectDir.resolve("build/reports/quality/production-dependency-graph.json").readText()
                assertTrue(inventory.contains("|root|root=project:"))
                assertTrue(inventory.contains("parent=project::domain:location"))
                assertTrue(inventory.contains("parent=project::domain:settings"))
                assertTrue(inventory.contains("|requested="))
                assertTrue(inventory.contains("path=project::domain:station>project::core:observability"))
                assertTrue(inventory.contains("path=project::domain:station>project::domain:location"))
                assertTrue(inventory.contains("path=project::domain:station>project::domain:settings"))
                assertTrue(inventory.contains("path=project::domain:station>project::domain:location>project::core:observability"))
                assertTrue(inventory.contains("path=project::domain:station>project::domain:settings>project::core:observability"))
                assertTrue(inventory.contains("path=project::domain:station>project::core:model>project::domain:station"))
            }
        }
    }

    @Test
    fun unresolvedLocalGraphPreservesRootParentRequestedSelectorAndPath() {
        val project = newProject("resolved-graph-unresolved")
            .writeProductionDependencyAndroidFixture(TestedTargetMutation.UNRESOLVED)

        val result = project.runner("productionDependencyInventory", "--rerun-tasks").buildAndFail()

        result.assertTaskOutcome(":productionDependencyInventory", TaskOutcome.FAILED)
        assertTrue(result.output.contains("unresolved production dependency graph entries"))
        val report = project.projectDir.resolve("build/reports/quality/production-dependency-graph.json").readText()
        assertTrue(report.contains("|unresolved|root=project:"))
        assertTrue(report.contains("parent=project::domain:station"))
        assertTrue(report.contains("requested=invalid.example:never-resolve:1.0"))
        assertTrue(report.contains("path=project::domain:station>unresolved:invalid.example:never-resolve:1.0"))
    }

    private fun newProject(name: String): GradlePluginTestProject =
        GradlePluginTestProject.create(
            temporaryFolder.newFolder("$name-root"),
            sharedGradleUserHome,
        )
}
