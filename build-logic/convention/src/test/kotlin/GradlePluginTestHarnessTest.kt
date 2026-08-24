package com.gasstation.buildlogic

import com.gasstation.buildlogic.testing.GradlePluginTestProject
import com.gasstation.buildlogic.testing.GradlePluginTestRunnerMode
import com.gasstation.buildlogic.testing.PreparedGradleRunnerArguments
import com.gasstation.buildlogic.testing.assertConfigurationCacheStored
import com.gasstation.buildlogic.testing.assertOutputContainsExactlyOnce
import com.gasstation.buildlogic.testing.assertOutputDoesNotContain
import com.gasstation.buildlogic.testing.assertOutputKeyValueExactlyOnce
import com.gasstation.buildlogic.testing.assertTaskOutcome
import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission.OWNER_READ
import java.nio.file.attribute.PosixFilePermission.OWNER_WRITE
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

internal class GradlePluginHarnessEnvironmentSuccessTest : GradlePluginTestHarnessSupport() {
    @Test
    fun successfulPluginBuildReportsIsolatedEnvironmentAndExactSentinels() {
        val project = newProject("success").writeSettings().writeBuildFile(successBuildScript())
            .writeFile("src/main/kotlin/Fixture.kt", "package fixture\n\nclass Fixture")

        val result = project.runner("spotlessCheck", "harnessEnvironment").build()

        result.assertTaskOutcome(":spotlessCheck", TaskOutcome.SUCCESS)
        result.assertTaskOutcome(":harnessEnvironment", TaskOutcome.SUCCESS)
        val reportedGradleVersion =
            result.assertOutputKeyValueExactlyOnce("HARNESS_GRADLE_VERSION", "9.6.1")
        val reportedGradleUserHome =
            result.assertOutputKeyValueExactlyOnce(
                "HARNESS_GRADLE_USER_HOME",
                project.gradleUserHomeDir.canonicalPath,
            )
        val reportedGradleUserHomeFile = File(reportedGradleUserHome)
        assertEquals(
            "reported Gradle user home must already be canonical",
            reportedGradleUserHome,
            reportedGradleUserHomeFile.canonicalPath,
        )
        assertEquals(project.gradleUserHomeDir.canonicalFile, reportedGradleUserHomeFile.canonicalFile)
        result.assertOutputContainsExactlyOnce("HARNESS_SUCCESS")
        result.assertOutputDoesNotContain("HARNESS_FABRICATED_FAILURE")
        result.assertOutputKeyValueExactlyOnce("HARNESS_MAX_WORKERS", "2")
        result.assertOutputKeyValueExactlyOnce("HARNESS_WORKER_ENVIRONMENT_CLEAN", "true")

        val configurationCacheResult =
            project.configurationCacheRunner("harnessWorkerProbe").build()
        configurationCacheResult.assertTaskOutcome(":harnessWorkerProbe", TaskOutcome.SUCCESS)
        configurationCacheResult.assertConfigurationCacheStored()
        configurationCacheResult.assertOutputKeyValueExactlyOnce(
            "HARNESS_CONFIGURATION_CACHE_MAX_WORKERS",
            "2",
        )

        val javaVersionMatches =
            Regex("""(?m)^HARNESS_JAVA_VERSION=(\d+)$""").findAll(result.output).toList()
        assertEquals("exactly one Java-version sentinel", 1, javaVersionMatches.size)
        assertTrue(
            "fixture JVM must be compatible with Java 17",
            javaVersionMatches.single().groupValues[1].toInt() >= 17,
        )
        assertNotEquals(
            File(System.getProperty("user.home"), ".gradle").canonicalFile,
            reportedGradleUserHomeFile.canonicalFile,
        )
        println("HARNESS_VERIFIED_GRADLE_VERSION=$reportedGradleVersion")
        println("HARNESS_VERIFIED_JAVA_VERSION=${javaVersionMatches.single().groupValues[1]}")
        println("HARNESS_VERIFIED_GRADLE_USER_HOME=$reportedGradleUserHome")

        val missingTaskError =
            assertThrows(AssertionError::class.java) {
                result.assertTaskOutcome(":missingHarnessTask", TaskOutcome.SUCCESS)
            }
        assertTrue(missingTaskError.message.orEmpty().contains(":missingHarnessTask"))
        assertTrue(missingTaskError.message.orEmpty().contains(":spotlessCheck=SUCCESS"))

        val wrongOutcomeError =
            assertThrows(AssertionError::class.java) {
                result.assertTaskOutcome(":harnessEnvironment", TaskOutcome.FAILED)
            }
        assertTrue(wrongOutcomeError.message.orEmpty().contains(":harnessEnvironment"))
        assertTrue(wrongOutcomeError.message.orEmpty().contains("expected=FAILED"))
        assertTrue(wrongOutcomeError.message.orEmpty().contains("actual=SUCCESS"))
    }

    @Test
    fun structuredEnvironmentAssertionRejectsVersionSuffix() {
        val project =
            newProject("wrong-version").writeSettings().writeBuildFile(
                successBuildScript(gradleVersionSuffix = "-WRONG"),
            )

        val result = project.runner("harnessEnvironment").build()

        assertThrows(AssertionError::class.java) {
            result.assertOutputKeyValueExactlyOnce("HARNESS_GRADLE_VERSION", "9.6.1")
        }
    }
}

internal class GradlePluginHarnessEnvironmentRejectionTest : GradlePluginTestHarnessSupport() {
    @Test
    fun structuredEnvironmentAssertionRejectsActualNestedGradleHome() {
        val project =
            newProject("nested-home").writeSettings().writeBuildFile(successBuildScript())
        val nestedHome = project.gradleUserHomeDir.resolve("nested").canonicalFile

        val result =
            project.adversarialRunner(nestedHome, "harnessEnvironment").build()

        result.assertOutputKeyValueExactlyOnce("HARNESS_MAX_WORKERS", "2")
        result.assertOutputKeyValueExactlyOnce("HARNESS_WORKER_ENVIRONMENT_CLEAN", "true")

        assertThrows(AssertionError::class.java) {
            result.assertOutputKeyValueExactlyOnce(
                "HARNESS_GRADLE_USER_HOME",
                project.gradleUserHomeDir.canonicalPath,
            )
        }
    }

    @Test
    fun structuredEnvironmentAssertionRejectsWhitespaceAndDuplicateKeyLines() {
        val project =
            newProject("malformed-environment").writeSettings().writeBuildFile(
                malformedEnvironmentBuildScript(),
            )

        val result = project.runner("malformedEnvironment").build()

        listOf("HARNESS_LEADING_SPACE", "HARNESS_TRAILING_SPACE", "HARNESS_DUPLICATE_KEY")
            .forEach { key ->
                assertThrows(AssertionError::class.java) {
                    result.assertOutputKeyValueExactlyOnce(key, "value")
                }
            }
    }
}

internal class GradlePluginHarnessFailureAssertionsTest : GradlePluginTestHarnessSupport() {
    @Test
    fun intentionalFailureRequiresTheNamedFailedTaskAndUniqueSentinel() {
        val project = newProject("failure").writeSettings().writeBuildFile(failureBuildScript())

        val result = project.runner("intentionalHarnessFailure").buildAndFail()

        result.assertTaskOutcome(":intentionalHarnessFailure", TaskOutcome.FAILED)
        result.assertOutputContainsExactlyOnce(INTENTIONAL_FAILURE)
        result.assertOutputDoesNotContain("HARNESS_SUCCESS")
        assertThrows(UnexpectedBuildFailure::class.java) {
            project.runner("intentionalHarnessFailure").build()
        }
    }

    @Test
    fun outputAssertionsRejectMissingDuplicateAndBlankSentinels() {
        val project = newProject("sentinels").writeSettings().writeBuildFile(
            successBuildScript(
                extraOutput = "println(\"HARNESS_DUPLICATE\")\nprintln(\"HARNESS_DUPLICATE\")",
            ),
        )
        val result = project.runner("harnessEnvironment").build()

        assertThrows(AssertionError::class.java) {
            result.assertOutputContainsExactlyOnce("HARNESS_MISSING")
        }
        assertThrows(AssertionError::class.java) {
            result.assertOutputContainsExactlyOnce("HARNESS_DUPLICATE")
        }
        assertThrows(IllegalArgumentException::class.java) {
            result.assertOutputContainsExactlyOnce("  ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            result.assertOutputDoesNotContain("")
        }
    }
}

internal class GradlePluginHarnessFileSafetyTest : GradlePluginTestHarnessSupport() {
    @Test
    fun builderWritesNestedUtf8WithExactlyOneFinalNewline() {
        val project = newProject("utf8")

        val returned = project.writeFile("nested/source.txt", "가격\n\n")

        assertEquals(project, returned)
        assertArrayEquals(
            "가격\n".toByteArray(UTF_8),
            project.projectDir.resolve("nested/source.txt").readBytes(),
        )
    }

    @Test
    fun builderRejectsAbsoluteTraversalAndResolvedSymlinkEscapes() {
        val project = newProject("unsafe-paths")
        val outside = temporaryFolder.newFolder("outside")
        Files.createSymbolicLink(project.projectDir.toPath().resolve("escape-link"), outside.toPath())

        assertThrows(IllegalArgumentException::class.java) {
            project.writeFile(outside.resolve("absolute.txt").absolutePath, "unsafe")
        }
        assertThrows(IllegalArgumentException::class.java) {
            project.writeFile("../traversal.txt", "unsafe")
        }
        assertThrows(IllegalArgumentException::class.java) {
            project.writeFile("safe/../alias.txt", "unsafe")
        }
        assertThrows(IllegalArgumentException::class.java) {
            project.writeFile("escape-link/outside.txt", "unsafe")
        }
        assertFalse(outside.resolve("outside.txt").exists())
    }
}

internal class GradlePluginHarnessRunnerPolicyTest : GradlePluginTestHarnessSupport() {
    @Test
    fun runnerRejectsEveryIsolationAndPolicyOverrideForm() {
        val project = newProject("runner-options")
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
                arrayOf("--build-cache"),
                arrayOf("--build-cache=true"),
                arrayOf("--no-build-cache"),
                arrayOf("--no-build-cache=true"),
                arrayOf("--warning-mode", "all"),
                arrayOf("--warning-mode=summary"),
                arrayOf("--warning-mode=fail"),
                arrayOf("--max-workers=2"),
            )

        conflicts.forEach { arguments ->
            val error =
                assertThrows(IllegalArgumentException::class.java) {
                    project.runner(*arguments)
                }
            assertTrue(
                "diagnostic must identify conflicting option ${arguments.first()}",
                error.message.orEmpty().contains(arguments.first()),
            )
        }
        assertWorkerControlGrammar(project)
        assertExecutorWorkerPropertyFailsClosed(project)
        assertGradlePropertyFilesFailClosed()
        assertNull("outer test executor must not inherit nested worker property", System.getProperty(WORKER_PROPERTY))
        assertNotSame(project.runner("help"), project.runner("help"))
    }

    @Test
    fun nestedBuildFailsWhenKnownRequiredChecksumIsRemovedFromOnlyFixtureCopy() {
        val project =
            newProject("missing-checksum").writeSettings().writeBuildFile(
                """
                plugins { base }
                repositories { mavenCentral() }
                val proof by configurations.creating
                dependencies { proof("org.jetbrains:annotations:13.0") }
                tasks.register("resolveProof") {
                    doLast { proof.files.forEach(File::getName) }
                }
                """.trimIndent(),
            )
        val runner = project.runner("resolveProof")
        val fixtureMetadata = project.projectDir.resolve("gradle/verification-metadata.xml")
        val original = fixtureMetadata.readText()
        val artifact =
            Regex(
                """\s*<artifact name="annotations-13\.0\.jar">.*?</artifact>""",
                RegexOption.DOT_MATCHES_ALL,
            )
        val mutated = original.replace(artifact, "")
        assertNotEquals("known fixture checksum mutation must change metadata", original, mutated)
        fixtureMetadata.writeText(mutated)

        val result = runner.buildAndFail()

        assertTrue(result.output.contains("Dependency verification failed"))
        assertTrue(result.output.contains("annotations-13.0.jar"))
    }
}

internal class GradlePluginHarnessIsolationTest : GradlePluginTestHarnessSupport() {
    @Test
    fun fiveConcurrentFixturesUseIsolatedProjectTestKitAndGradleHomes() {
        assertEquals("5", System.getProperty("gasstation.convention.test.maxParallelForks"))
        val roots = (1..5).map { index -> temporaryFolder.newFolder("parallel-$index-root") }
        val fixtures = roots.map(GradlePluginTestProject::create)
        fixtures.first().writeFile("stale.txt", "stale")

        listOf(
            fixtures.map { it.projectDir.canonicalFile },
            fixtures.map { it.testKitDir.canonicalFile },
            fixtures.map { it.gradleUserHomeDir.canonicalFile },
        ).forEach { directories -> assertEquals(5, directories.toSet().size) }
        fixtures.drop(1).forEach { fixture -> assertFalse(fixture.projectDir.resolve("stale.txt").exists()) }
        assertThrows(IllegalArgumentException::class.java) {
            GradlePluginTestProject.create(roots.first())
        }
    }
}

internal abstract class GradlePluginTestHarnessSupport {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    protected fun assertWorkerControlGrammar(project: GradlePluginTestProject) {
        val adversarialHome = temporaryFolder.newFolder("worker-adversarial-home")
        fun prepare(
            mode: GradlePluginTestRunnerMode,
            arguments: List<String>,
        ): PreparedGradleRunnerArguments =
            project.prepareArgumentsForTesting(
                mode,
                if (mode == GradlePluginTestRunnerMode.ADVERSARIAL) adversarialHome else project.gradleUserHomeDir,
                *arguments.toTypedArray(),
            )

        fun assertPreparedInEveryMode(arguments: List<String>) {
            GradlePluginTestRunnerMode.entries.forEach { mode ->
                val prepared = prepare(mode, arguments)
                assertEquals("caller argv must remain byte/order exact in $mode", arguments, prepared.arguments.take(arguments.size))
                assertEquals("fixture cap must be final in $mode", MAX_WORKERS_ARGUMENT, prepared.arguments.last())
                assertEquals(
                    "fixture cap must occur exactly once in $mode",
                    1,
                    prepared.arguments.count(MAX_WORKERS_ARGUMENT::equals),
                )
                val javaInstallationsIndex =
                    prepared.arguments.indexOfFirst { it.startsWith("-Dorg.gradle.java.installations.paths=") }
                assertEquals(
                    "fixture cap must immediately follow Java installation paths in $mode",
                    javaInstallationsIndex + 1,
                    prepared.arguments.lastIndex,
                )
            }
        }

        fun assertRejectedInEveryMode(arguments: List<String>, diagnostic: String) {
            GradlePluginTestRunnerMode.entries.forEach { mode ->
                val error =
                    assertThrows(IllegalArgumentException::class.java) {
                        prepare(mode, arguments)
                    }
                assertTrue(
                    "$mode rejection must identify $diagnostic: ${error.message}",
                    error.message.orEmpty().contains(diagnostic),
                )
            }
        }

        GradlePluginTestRunnerMode.entries.forEach { mode ->
            val prepared = prepare(mode, listOf("help"))
            assertEquals(1, prepared.arguments.count(MAX_WORKERS_ARGUMENT::equals))
            assertEquals(MAX_WORKERS_ARGUMENT, prepared.arguments.last())
        }

        val directValues = listOf("", "2", "3", "two", "two=extra")
        directValues.forEach { value ->
            listOf(
                listOf("--max-workers=$value"),
                listOf("--max-workers", value),
                listOf("-max-workers", value),
            ).forEach { arguments ->
                assertRejectedInEveryMode(arguments, arguments.first())
            }
        }
        listOf(listOf("--max-workers"), listOf("-max-workers")).forEach { arguments ->
            assertRejectedInEveryMode(arguments, arguments.first())
        }

        directValues.forEach { value ->
            assertPreparedInEveryMode(listOf("-max-workers=$value"))
        }
        directValues.drop(1).forEach { value ->
            listOf(
                listOf("--max-workers$value"),
                listOf("-max-workers$value"),
                listOf("max-workers", value),
            ).forEach(::assertPreparedInEveryMode)
        }

        val workerPayloads =
            listOf(
                WORKER_PROPERTY,
                "$WORKER_PROPERTY=",
                "$WORKER_PROPERTY=2",
                "$WORKER_PROPERTY=3",
                "$WORKER_PROPERTY=two=extra",
            )
        val unrelatedPayloads =
            listOf(
                "example.key",
                "example.key=",
                "example.key=value",
                "example.key=$WORKER_PROPERTY=2",
                "example.$WORKER_PROPERTY.suffix=value",
                "x$WORKER_PROPERTY=value",
                "$WORKER_PROPERTY.suffix=value",
            )
        val constructions = acceptedPropertyConstructions()
        assertEquals("accepted property construction count", 16, constructions.size)
        workerPayloads.forEach { payload ->
            constructions.forEach { construction ->
                assertRejectedInEveryMode(construction.render(payload), WORKER_PROPERTY)
            }
        }

        constructions.filter(PropertyConstruction::equalsForm).forEach { construction ->
            assertRejectedInEveryMode(construction.render(""), "empty payload")
        }
        constructions.forEach { construction ->
            val arguments = construction.render("=value")
            if (construction.shortJoined) {
                GradlePluginTestRunnerMode.entries.forEach { mode ->
                    val prepared = prepare(mode, arguments)
                    assertEquals("short equals must win over short joined", "value", prepared.callerProperties.single().key)
                    assertEquals("", prepared.callerProperties.single().value)
                }
            } else {
                assertRejectedInEveryMode(arguments, "empty key")
            }
        }

        val separatedOptions =
            listOf("-D", "-P", "--D", "--P", "--system-prop", "--project-prop", "-system-prop", "-project-prop")
        val optionLikePayloads = listOf("---", "-=value", "-x", "--offline", "-Pother=value")
        separatedOptions.forEach { option ->
            assertRejectedInEveryMode(listOf(option), "missing")
            assertRejectedInEveryMode(listOf(option, ""), "empty")
            GradlePluginTestRunnerMode.entries.forEach { mode ->
                val loneDash = prepare(mode, listOf(option, "-"))
                assertEquals(listOf(option, "-"), loneDash.arguments.take(2))
                assertEquals("-", loneDash.callerProperties.single().key)
                assertEquals("", loneDash.callerProperties.single().value)
                assertEquals(2, loneDash.callerProperties.single().consumedArgumentCount)
            }
            listOf(listOf(option, "--"), listOf(option, "--", "task")).forEach { arguments ->
                assertRejectedInEveryMode(arguments, "terminator")
            }
            optionLikePayloads.forEach { next ->
                assertRejectedInEveryMode(listOf(option, next), next)
            }
            assertPreparedInEveryMode(listOf(option, "example.key=value"))
        }

        unrelatedPayloads.forEach { payload ->
            val expectedSeparator = payload.indexOf('=')
            val expectedKey = if (expectedSeparator < 0) payload else payload.substring(0, expectedSeparator)
            val expectedValue = if (expectedSeparator < 0) "" else payload.substring(expectedSeparator + 1)
            constructions.forEach { construction ->
                val propertyArguments = construction.render(payload)
                listOf(
                    listOf("help") + propertyArguments + "--rerun-tasks",
                    propertyArguments + listOf("help", "--rerun-tasks"),
                ).forEach { arguments ->
                    GradlePluginTestRunnerMode.entries.forEach { mode ->
                        val prepared = prepare(mode, arguments)
                        assertEquals(arguments, prepared.arguments.take(arguments.size))
                        val decoded = prepared.callerProperties.single()
                        assertEquals(expectedKey, decoded.key)
                        assertEquals(expectedValue, decoded.value)
                    }
                }
            }
        }

        (workerPayloads + unrelatedPayloads).forEach { payload ->
            unsupportedPropertyConstructions(payload).forEach(::assertPreparedInEveryMode)
        }
        listOf("-system-prop=", "-project-prop=").forEach { unsupported ->
            assertPreparedInEveryMode(listOf(unsupported))
        }
        listOf("-D=value", "-P=value").forEach { shortEquals ->
            GradlePluginTestRunnerMode.entries.forEach { mode ->
                val prepared = prepare(mode, listOf(shortEquals))
                assertEquals("value", prepared.callerProperties.single().key)
                assertEquals("", prepared.callerProperties.single().value)
            }
        }
    }

    protected fun assertExecutorWorkerPropertyFailsClosed(project: GradlePluginTestProject) {
        val previous = System.getProperty(WORKER_PROPERTY)
        try {
            System.setProperty(WORKER_PROPERTY, "2")
            GradlePluginTestRunnerMode.entries.forEach { mode ->
                val error =
                    assertThrows(IllegalArgumentException::class.java) {
                        project.prepareArgumentsForTesting(mode, project.gradleUserHomeDir, "help")
                    }
                assertTrue(error.message.orEmpty().contains("executor JVM property"))
            }
        } finally {
            if (previous == null) {
                System.clearProperty(WORKER_PROPERTY)
            } else {
                System.setProperty(WORKER_PROPERTY, previous)
            }
        }
    }

    protected fun assertGradlePropertyFilesFailClosed() {
        val valid = newProject("valid-worker-properties")
        val projectProperties = valid.projectDir.resolve("gradle.properties")
        val userProperties = valid.gradleUserHomeDir.resolve("gradle.properties")
        projectProperties.writeText("example.project=value\n", UTF_8)
        userProperties.writeText("example.user=value\n", UTF_8)
        val projectBytes = projectProperties.readBytes()
        val userBytes = userProperties.readBytes()
        GradlePluginTestRunnerMode.entries.forEach { mode ->
            valid.prepareArgumentsForTesting(mode, valid.gradleUserHomeDir, "help")
        }
        assertArrayEquals(projectBytes, projectProperties.readBytes())
        assertArrayEquals(userBytes, userProperties.readBytes())

        var mutationIndex = 0
        fun assertMutation(
            surface: PropertySurface,
            label: String,
            prepareFile: (File) -> Unit,
        ) {
            val project = newProject("worker-properties-${surface.name.lowercase()}-${mutationIndex++}-$label")
            val candidate =
                when (surface) {
                    PropertySurface.PROJECT -> project.projectDir.resolve("gradle.properties")
                    PropertySurface.USER_HOME -> project.gradleUserHomeDir.resolve("gradle.properties")
                }
            prepareFile(candidate)
            try {
                GradlePluginTestRunnerMode.entries.forEach { mode ->
                    val error =
                        assertThrows(IllegalArgumentException::class.java) {
                            project.prepareArgumentsForTesting(mode, project.gradleUserHomeDir, "help")
                        }
                    assertTrue("$surface/$label diagnostic: ${error.message}", error.message.orEmpty().contains("gradle.properties"))
                }
            } finally {
                if (Files.exists(candidate.toPath()) && !Files.isSymbolicLink(candidate.toPath()) && candidate.isFile) {
                    Files.setPosixFilePermissions(candidate.toPath(), setOf(OWNER_READ, OWNER_WRITE))
                }
            }
        }

        PropertySurface.entries.forEach { surface ->
            listOf(
                "target" to "$WORKER_PROPERTY=2\n",
                "system-prop" to "systemProp.$WORKER_PROPERTY=2\n",
                "jvmargs" to "org.gradle.jvmargs=-Xmx1g -D$WORKER_PROPERTY=2\n",
                "malformed" to "broken=\\uZZZZ\n",
            ).forEach { (label, content) ->
                assertMutation(surface, label) { file -> file.writeText(content, UTF_8) }
            }
            assertMutation(surface, "symlink") { file ->
                val target = temporaryFolder.newFile("worker-properties-link-target-${mutationIndex++}")
                target.writeText("example=value\n", UTF_8)
                Files.createSymbolicLink(file.toPath(), target.toPath())
            }
            assertMutation(surface, "nonregular") { file -> assertTrue(file.mkdir()) }
            assertMutation(surface, "unreadable") { file ->
                file.writeText("example=value\n", UTF_8)
                Files.setPosixFilePermissions(file.toPath(), emptySet())
            }
        }
    }

    private fun acceptedPropertyConstructions(): List<PropertyConstruction> =
        listOf(
            PropertyConstruction("system-short-joined", shortJoined = true) { payload -> listOf("-D$payload") },
            PropertyConstruction("system-short-separated") { payload -> listOf("-D", payload) },
            PropertyConstruction("system-short-equals", equalsForm = true) { payload -> listOf("-D=$payload") },
            PropertyConstruction("system-double-short-equals", equalsForm = true) { payload -> listOf("--D=$payload") },
            PropertyConstruction("system-double-short-separated") { payload -> listOf("--D", payload) },
            PropertyConstruction("system-double-long-equals", equalsForm = true) { payload -> listOf("--system-prop=$payload") },
            PropertyConstruction("system-double-long-separated") { payload -> listOf("--system-prop", payload) },
            PropertyConstruction("system-single-long-separated") { payload -> listOf("-system-prop", payload) },
            PropertyConstruction("project-short-joined", shortJoined = true) { payload -> listOf("-P$payload") },
            PropertyConstruction("project-short-separated") { payload -> listOf("-P", payload) },
            PropertyConstruction("project-short-equals", equalsForm = true) { payload -> listOf("-P=$payload") },
            PropertyConstruction("project-double-short-equals", equalsForm = true) { payload -> listOf("--P=$payload") },
            PropertyConstruction("project-double-short-separated") { payload -> listOf("--P", payload) },
            PropertyConstruction("project-double-long-equals", equalsForm = true) { payload -> listOf("--project-prop=$payload") },
            PropertyConstruction("project-double-long-separated") { payload -> listOf("--project-prop", payload) },
            PropertyConstruction("project-single-long-separated") { payload -> listOf("-project-prop", payload) },
        )

    private fun unsupportedPropertyConstructions(payload: String): List<List<String>> =
        listOf(
            listOf("-system-prop=$payload"),
            listOf("-project-prop=$payload"),
            listOf("--D$payload"),
            listOf("--P$payload"),
            listOf("--system-prop$payload"),
            listOf("--project-prop$payload"),
            listOf("-system-prop$payload"),
            listOf("-project-prop$payload"),
        )

    protected fun newProject(name: String): GradlePluginTestProject =
        GradlePluginTestProject.create(temporaryFolder.newFolder("$name-root"))

    protected fun successBuildScript(
        extraOutput: String = "",
        gradleVersionSuffix: String = "",
    ): String {
        val extraOutputSuffix =
            extraOutput.takeIf(String::isNotBlank)?.lineSequence()?.joinToString(
                prefix = "\n",
                separator = "\n",
            ) { "                $it" }.orEmpty()
        return """
        plugins {
            id("gasstation.spotless")
        }

        spotless {
            kotlin {
                clearSteps()
                trimTrailingWhitespace()
                endWithNewline()
            }
            kotlinGradle {
                clearSteps()
                trimTrailingWhitespace()
                endWithNewline()
            }
        }

        abstract class HarnessWorkerProbe : org.gradle.api.DefaultTask() {
            @get:org.gradle.api.tasks.Input
            abstract val maxWorkers: org.gradle.api.provider.Property<Int>

            @org.gradle.api.tasks.TaskAction
            fun probe() {
                println("HARNESS_CONFIGURATION_CACHE_MAX_WORKERS=" + maxWorkers.get())
            }
        }

        tasks.register<HarnessWorkerProbe>("harnessWorkerProbe") {
            maxWorkers.set(gradle.startParameter.maxWorkerCount)
        }

        tasks.register("harnessEnvironment") {
            doLast {
                println("HARNESS_GRADLE_VERSION=${'$'}{gradle.gradleVersion}$gradleVersionSuffix")
                println("HARNESS_JAVA_VERSION=${'$'}{JavaVersion.current().majorVersion}")
                println("HARNESS_GRADLE_USER_HOME=${'$'}{gradle.gradleUserHomeDir.canonicalPath}")
                println("HARNESS_MAX_WORKERS=${'$'}{gradle.startParameter.maxWorkerCount}")
                val forbiddenWorkerEnvironment =
                    listOf(
                        "GRADLE_OPTS",
                        "JAVA_OPTS",
                        "JAVA_TOOL_OPTIONS",
                        "JDK_JAVA_OPTIONS",
                        "_JAVA_OPTIONS",
                        "ORG_GRADLE_PROJECT_org.gradle.workers.max",
                        "GRADLE_USER_HOME",
                    )
                val workerEnvironmentClean =
                    forbiddenWorkerEnvironment.none { System.getenv(it) != null } &&
                        System.getenv().values.none { it.contains("org.gradle.workers.max") }
                println("HARNESS_WORKER_ENVIRONMENT_CLEAN=${'$'}workerEnvironmentClean")
                println("HARNESS_SUCCESS")$extraOutputSuffix
            }
        }
        """.trimIndent()
    }

    protected fun malformedEnvironmentBuildScript(): String =
        """
        plugins {
            id("gasstation.spotless")
        }

        tasks.register("malformedEnvironment") {
            doLast {
                println(" HARNESS_LEADING_SPACE=value")
                println("HARNESS_TRAILING_SPACE=value ")
                println("HARNESS_DUPLICATE_KEY=value")
                println("HARNESS_DUPLICATE_KEY=value")
            }
        }
        """.trimIndent()

    protected fun failureBuildScript(): String =
        """
        plugins {
            id("gasstation.spotless")
        }

        tasks.register("intentionalHarnessFailure") {
            doLast {
                println("$INTENTIONAL_FAILURE")
                throw GradleException("Intentional TestKit harness failure")
            }
        }
        """.trimIndent()

    protected companion object {
        const val INTENTIONAL_FAILURE = "HARNESS_INTENTIONAL_FAILURE_4A"
        const val MAX_WORKERS_ARGUMENT = "--max-workers=2"
        const val WORKER_PROPERTY = "org.gradle.workers.max"
    }
}

private data class PropertyConstruction(
    val name: String,
    val equalsForm: Boolean = false,
    val shortJoined: Boolean = false,
    val render: (String) -> List<String>,
)

private enum class PropertySurface {
    PROJECT,
    USER_HOME,
}
