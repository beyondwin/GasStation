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
    fun realAgpAndResolvedGraphContractsAreCoveredByOneMultiProjectBuild() {
        val project = newProject("tested-target-all-invalid")
            .writeProductionDependencyAndroidFixture(TestedTargetMutation.ALL_INVALID)

        val result =
            project.runner(
                "verifyModuleBoundaries",
                "productionDependencyInventory",
                "--continue",
                "--rerun-tasks",
            ).buildAndFail()

        result.assertTaskOutcome(":verifyModuleBoundaries", TaskOutcome.FAILED)
        result.assertTaskOutcome(":productionDependencyInventory", TaskOutcome.FAILED)
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
        assertTrue(report.contains("path=project::domain:station>project::core:model>project::domain:station"))
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
