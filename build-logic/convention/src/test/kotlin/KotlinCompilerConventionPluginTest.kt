package com.gasstation.buildlogic

import com.gasstation.buildlogic.testing.GradlePluginTestProject
import com.gasstation.buildlogic.testing.KotlinConventionFixtureKind
import com.gasstation.buildlogic.testing.assertConfigurationCacheReused
import com.gasstation.buildlogic.testing.assertConfigurationCacheStored
import com.gasstation.buildlogic.testing.assertOutputKeyValueExactlyOnce
import com.gasstation.buildlogic.testing.assertTaskOutcome
import com.gasstation.buildlogic.testing.compiledClassFile
import com.gasstation.buildlogic.testing.readJvmClassMajorVersion
import com.gasstation.buildlogic.testing.writeKotlinConventionFixture
import com.gasstation.buildlogic.testing.writeJvmKotlinConventionMultiProjectFixture
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class KotlinCompilerConventionPluginTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val sharedGradleUserHome by lazy {
        temporaryFolder.newFolder("kotlin-gradle-user-home")
    }

    @Test
    fun jvmReportOnlyModuleKeepsWarningForAbsentAndExactFalseProperty() {
        val project = newProject("jvm-report-only")
            .writeKotlinConventionFixture(KotlinConventionFixtureKind.JVM)

        listOf(emptyArray(), arrayOf("-Pgasstation.kotlinWarningsAsErrors=false"))
            .forEachIndexed { index, propertyArguments ->
                val result =
                    project.runner("compileKotlin", "--rerun-tasks", *propertyArguments).build()

                result.assertTaskOutcome(":compileKotlin", TaskOutcome.SUCCESS)
                assertUncheckedCastWarning(result, "report-only run $index")
            }
    }

    @Test
    fun exactTrueMakesTheJvmWarningFatal() {
        val project = newProject("jvm-opt-in")
            .writeKotlinConventionFixture(KotlinConventionFixtureKind.JVM)

        val result =
            project.runner(
                "compileKotlin",
                "--rerun-tasks",
                "-Pgasstation.kotlinWarningsAsErrors=true",
            ).buildAndFail()

        result.assertTaskOutcome(":compileKotlin", TaskOutcome.FAILED)
        assertUncheckedCastWarning(result, "strict opt-in")
    }

    @Test
    fun everyDomainAndOnlyTheTwoApprovedCoreModulesAreStrictByDefault() {
        val strictProjectPaths = listOf(":domain:sample", ":core:model", ":core:observability")
        val networkProjectPath = ":core:network"
        val project = newProject("strict-module-matrix")
            .writeJvmKotlinConventionMultiProjectFixture(
                strictProjectPaths + networkProjectPath,
            )
        val compileTasks =
            (strictProjectPaths + networkProjectPath).map { projectPath ->
                "$projectPath:compileKotlin"
            }
        val explicitApiProbes = strictProjectPaths.map { "$it:explicitApiProbe" }

        listOf(emptyArray(), arrayOf("-Pgasstation.kotlinWarningsAsErrors=false"))
            .forEach { propertyArguments ->
                val result =
                    project.runner(
                        *compileTasks.toTypedArray(),
                        *explicitApiProbes.toTypedArray(),
                        "--rerun-tasks",
                        "--continue",
                        *propertyArguments,
                    ).buildAndFail()

                strictProjectPaths.forEach { projectPath ->
                    result.assertTaskOutcome("$projectPath:compileKotlin", TaskOutcome.FAILED)
                }
                result.assertTaskOutcome("$networkProjectPath:compileKotlin", TaskOutcome.SUCCESS)
                assertUncheckedCastWarning(result, "strict module matrix")
                assertTrue(result.output.contains("EXPLICIT_API_ARGUMENTS=[-Xexplicit-api=strict]"))
            }
    }

    @Test
    fun invalidWarningPropertyValuesFailBeforeACompileCanSucceed() {
        val project = newProject("invalid-warning-property")
            .writeKotlinConventionFixture(KotlinConventionFixtureKind.JVM, warnedSource = false)

        listOf("TRUE", "False", " true", "false ", "yes", "").forEach { invalid ->
            val result =
                project.runner(
                    "compileKotlin",
                    "--rerun-tasks",
                    "-Pgasstation.kotlinWarningsAsErrors=$invalid",
                ).buildAndFail()

            assertTrue(
                "missing strict parser diagnostic for '$invalid'",
                result.output.contains(
                    "gasstation.kotlinWarningsAsErrors must be exactly true or false",
                ),
            )
            assertFalse(
                result.tasks.any { task ->
                    task.path == ":compileKotlin" && task.outcome == TaskOutcome.SUCCESS
                },
            )
        }
    }

    @Test
    fun applicationAndAndroidLibraryKeepTargetTimeoutAndReportOnlyDefault() {
        listOf(
            KotlinConventionFixtureKind.ANDROID_APPLICATION,
            KotlinConventionFixtureKind.ANDROID_LIBRARY,
        ).forEach { kind ->
            val project = newProject("android-${kind.name.lowercase()}")
                .writeKotlinConventionFixture(kind)

            val defaultResult =
                project.runner("conventionProbe", "--rerun-tasks").build()
            defaultResult.assertTaskOutcome(":${kind.compileTask}", TaskOutcome.SUCCESS)
            defaultResult.assertTaskOutcome(":conventionProbe", TaskOutcome.SUCCESS)
            defaultResult.assertOutputKeyValueExactlyOnce("CONVENTION_JVM_TARGET", "17")
            defaultResult.assertOutputKeyValueExactlyOnce(
                "CONVENTION_WARNINGS_AS_ERRORS",
                "false",
            )
            defaultResult.assertOutputKeyValueExactlyOnce(
                "CONVENTION_TEST_TIMEOUT_MINUTES",
                "15",
            )
            assertUncheckedCastWarning(defaultResult, "${kind.name} report-only")

            val strictResult =
                project.runner(
                    kind.compileTask,
                    "--rerun-tasks",
                    "-Pgasstation.kotlinWarningsAsErrors=true",
                ).buildAndFail()
            strictResult.assertTaskOutcome(":${kind.compileTask}", TaskOutcome.FAILED)
            assertUncheckedCastWarning(strictResult, "${kind.name} strict opt-in")
        }
    }

    @Test
    fun jvmConventionKeepsTarget17ClassMajor61AndExactTestTimeout() {
        val project = newProject("jvm-model")
            .writeKotlinConventionFixture(
                kind = KotlinConventionFixtureKind.JVM,
                warnedSource = false,
            )

        val result = project.runner("conventionProbe", "--rerun-tasks").build()

        result.assertTaskOutcome(":compileKotlin", TaskOutcome.SUCCESS)
        result.assertTaskOutcome(":conventionProbe", TaskOutcome.SUCCESS)
        result.assertOutputKeyValueExactlyOnce("CONVENTION_JVM_TARGET", "17")
        result.assertOutputKeyValueExactlyOnce("CONVENTION_WARNINGS_AS_ERRORS", "false")
        result.assertOutputKeyValueExactlyOnce("CONVENTION_TEST_TIMEOUT_MINUTES", "15")
        val classFile = project.compiledClassFile(KotlinConventionFixtureKind.JVM)
        assertTrue("compiled class missing at $classFile", classFile.isFile)
        assertTrue(
            "expected Java 17 class major 61",
            classFile.readJvmClassMajorVersion() == 61,
        )
    }

    @Test
    fun jvmConventionStoresReusesAndReevaluatesTheWarningProperty() {
        val project = newProject("jvm-configuration-cache")
            .writeKotlinConventionFixture(
                kind = KotlinConventionFixtureKind.JVM,
                warnedSource = false,
            )

        val defaultArguments = arrayOf("conventionProbe", "--rerun-tasks")
        val first = project.configurationCacheRunner(*defaultArguments).build()
        first.assertTaskOutcome(":compileKotlin", TaskOutcome.SUCCESS)
        first.assertTaskOutcome(":conventionProbe", TaskOutcome.SUCCESS)
        first.assertConfigurationCacheStored()
        first.assertOutputKeyValueExactlyOnce("CONVENTION_WARNINGS_AS_ERRORS", "false")
        val second = project.configurationCacheRunner(*defaultArguments).build()
        second.assertTaskOutcome(":compileKotlin", TaskOutcome.SUCCESS)
        second.assertTaskOutcome(":conventionProbe", TaskOutcome.SUCCESS)
        second.assertConfigurationCacheReused()
        second.assertOutputKeyValueExactlyOnce("CONVENTION_WARNINGS_AS_ERRORS", "false")

        val strictArguments =
            arrayOf(
                "conventionProbe",
                "--rerun-tasks",
                "-Pgasstation.kotlinWarningsAsErrors=true",
            )
        val strictFirst = project.configurationCacheRunner(*strictArguments).build()
        strictFirst.assertTaskOutcome(":compileKotlin", TaskOutcome.SUCCESS)
        strictFirst.assertTaskOutcome(":conventionProbe", TaskOutcome.SUCCESS)
        strictFirst.assertConfigurationCacheReused()
        strictFirst.assertOutputKeyValueExactlyOnce("CONVENTION_WARNINGS_AS_ERRORS", "true")
        val strictSecond = project.configurationCacheRunner(*strictArguments).build()
        strictSecond.assertTaskOutcome(":compileKotlin", TaskOutcome.SUCCESS)
        strictSecond.assertTaskOutcome(":conventionProbe", TaskOutcome.SUCCESS)
        strictSecond.assertConfigurationCacheReused()
        strictSecond.assertOutputKeyValueExactlyOnce("CONVENTION_WARNINGS_AS_ERRORS", "true")
    }

    @Test
    fun androidConventionsStoreReuseAndReevaluateTheWarningProperty() {
        listOf(
            KotlinConventionFixtureKind.ANDROID_APPLICATION,
            KotlinConventionFixtureKind.ANDROID_LIBRARY,
        ).forEach { kind ->
            val project = newProject("android-cache-${kind.name.lowercase()}")
                .writeKotlinConventionFixture(kind, warnedSource = false)
            val defaultArguments = arrayOf("conventionProbe", "--rerun-tasks")

            val first = project.configurationCacheRunner(*defaultArguments).build()
            first.assertTaskOutcome(":${kind.compileTask}", TaskOutcome.SUCCESS)
            first.assertTaskOutcome(":conventionProbe", TaskOutcome.SUCCESS)
            first.assertConfigurationCacheStored()
            first.assertOutputKeyValueExactlyOnce("CONVENTION_WARNINGS_AS_ERRORS", "false")
            val second = project.configurationCacheRunner(*defaultArguments).build()
            second.assertTaskOutcome(":${kind.compileTask}", TaskOutcome.SUCCESS)
            second.assertTaskOutcome(":conventionProbe", TaskOutcome.SUCCESS)
            second.assertConfigurationCacheReused()
            second.assertOutputKeyValueExactlyOnce("CONVENTION_WARNINGS_AS_ERRORS", "false")

            val strictArguments =
                arrayOf(
                    "conventionProbe",
                    "--rerun-tasks",
                    "-Pgasstation.kotlinWarningsAsErrors=true",
                )
            val strictFirst = project.configurationCacheRunner(*strictArguments).build()
            strictFirst.assertTaskOutcome(":${kind.compileTask}", TaskOutcome.SUCCESS)
            strictFirst.assertTaskOutcome(":conventionProbe", TaskOutcome.SUCCESS)
            strictFirst.assertConfigurationCacheReused()
            strictFirst.assertOutputKeyValueExactlyOnce("CONVENTION_WARNINGS_AS_ERRORS", "true")
            val strictSecond = project.configurationCacheRunner(*strictArguments).build()
            strictSecond.assertTaskOutcome(":${kind.compileTask}", TaskOutcome.SUCCESS)
            strictSecond.assertTaskOutcome(":conventionProbe", TaskOutcome.SUCCESS)
            strictSecond.assertConfigurationCacheReused()
            strictSecond.assertOutputKeyValueExactlyOnce("CONVENTION_WARNINGS_AS_ERRORS", "true")
        }
    }

    @Test
    fun bothRunnerModesRejectEveryCacheAndIsolationOverride() {
        val project = newProject("runner-policy")
        val conflicts =
            listOf(
                arrayOf("-g", "/tmp/other-home"),
                arrayOf("-g/tmp/other-home"),
                arrayOf("--gradle-user-home", "/tmp/other-home"),
                arrayOf("--gradle-user-home=/tmp/other-home"),
                arrayOf("--configuration-cache"),
                arrayOf("--configuration-cache=true"),
                arrayOf("--no-configuration-cache"),
                arrayOf("--no-configuration-cache=true"),
                arrayOf("--configuration-cache-problems", "warn"),
                arrayOf("--configuration-cache-problems=warn"),
                arrayOf("--build-cache"),
                arrayOf("--build-cache=true"),
                arrayOf("--no-build-cache"),
                arrayOf("--no-build-cache=true"),
                arrayOf("--warning-mode", "all"),
                arrayOf("--warning-mode=summary"),
            )

        conflicts.forEach { arguments ->
            assertThrows(IllegalArgumentException::class.java) { project.runner(*arguments) }
            assertThrows(IllegalArgumentException::class.java) {
                project.configurationCacheRunner(*arguments)
            }
        }
        assertNotSame(
            project.configurationCacheRunner("help"),
            project.configurationCacheRunner("help"),
        )
    }

    @Test
    fun configurationCacheRunnerKeepsProblemsFatal() {
        val project = newProject("configuration-cache-problems")
            .writeSettings()
            .writeBuildFile(
                """
                tasks.register("configurationCacheProblem") {
                    doLast {
                        println(project.name)
                    }
                }
                """.trimIndent(),
            )

        val result = project.configurationCacheRunner("configurationCacheProblem").buildAndFail()

        assertTrue(
            result.output.contains("invocation of 'Task.project' at execution time is unsupported"),
        )
        assertFalse(result.output.lineSequence().any { it == "Configuration cache entry stored." })
    }

    private fun newProject(name: String): GradlePluginTestProject =
        GradlePluginTestProject.create(
            temporaryFolder.newFolder("$name-root"),
            sharedGradleUserHome,
        )

    private fun assertUncheckedCastWarning(result: BuildResult, context: String) {
        assertTrue(
            "$context must expose the deterministic unchecked-cast diagnostic",
            result.output.lowercase().contains("unchecked cast"),
        )
    }

}
