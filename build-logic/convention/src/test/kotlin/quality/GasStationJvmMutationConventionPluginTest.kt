package com.gasstation.buildlogic.quality

import com.gasstation.buildlogic.quality.mutation.RejectDirectPitestAction
import com.gasstation.buildlogic.quality.mutation.configureSealedInheritedJavaExecDefaults
import com.gasstation.buildlogic.quality.mutation.blockingMutationThreshold
import com.gasstation.buildlogic.quality.mutation.requireSupportedMutationProject
import com.gasstation.buildlogic.quality.mutation.validateBlockingEnforcement
import com.gasstation.buildlogic.testing.GradlePluginTestProject
import info.solidsoft.gradle.pitest.validatePitestOptionOverrides
import info.solidsoft.gradle.pitest.canonicalIdentity
import info.solidsoft.gradle.pitest.validateSealedExecutable
import info.solidsoft.gradle.pitest.validateSealedEncodingSurface
import info.solidsoft.gradle.pitest.validateEffectivePitestSurface
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.api.tasks.JavaExec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File

class GasStationJvmMutationConventionPluginTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val sharedGradleUserHome by lazy {
        temporaryFolder.newFolder("mutation-gradle-user-home")
    }

    @Test
    fun blockingPhaseUsesExactNativeFloorsAndKeepsSettingsScoreReportOnly() {
        assertEquals(45, blockingMutationThreshold(":domain:station"))
        assertEquals(75, blockingMutationThreshold(":domain:location"))
        assertEquals(null, blockingMutationThreshold(":domain:settings"))

        validateBlockingEnforcement("blocking", "blocking")
        listOf("observe" to "blocking", "blocking" to "observe").forEach { (taskPhase, policyPhase) ->
            val failure = assertThrows(GradleException::class.java) {
                validateBlockingEnforcement(taskPhase, policyPhase)
            }
            assertTrue(failure.message, failure.message.orEmpty().contains("blocking"))
        }
    }

    @Test
    fun sealedInheritedDefaultsExplicitlyOwnUtf8AndDebugSurface() {
        val task = ProjectBuilder.builder().build().tasks.create("sealedJava", JavaExec::class.java)
        task.defaultCharacterEncoding = "UTF-16"
        task.modularity.inferModulePath.set(true)

        configureSealedInheritedJavaExecDefaults(task)

        assertEquals("UTF-8", task.defaultCharacterEncoding)
        assertEquals(1, task.allJvmArgs.count { it == "-Dfile.encoding=UTF-8" })
        assertFalse(task.modularity.inferModulePath.get())
        assertFalse(task.debugOptions.enabled.get())
        assertEquals("localhost", task.debugOptions.host.get())
        assertEquals(5005, task.debugOptions.port.get())
        assertTrue(task.debugOptions.server.get())
        assertTrue(task.debugOptions.suspend.get())
    }

    @Test
    fun encodingSurfaceRejectsSameValueAlternateSourcesAndRequiresOneManagedArgument() {
        validateSealedEncodingSurface(
            defaultCharacterEncoding = "UTF-8",
            explicitJvmArguments = emptyList(),
            mutableSystemProperties = emptyMap<String, String>(),
            effectiveJvmArguments = listOf("-Dfile.encoding=UTF-8", "-Duser.language=en"),
        )

        val mutations: List<() -> Unit> =
            listOf(
            { validateSealedEncodingSurface(null, emptyList(), emptyMap<String, String>(), listOf("-Dfile.encoding=UTF-8")) },
            { validateSealedEncodingSurface("UTF-16", emptyList(), emptyMap<String, String>(), listOf("-Dfile.encoding=UTF-16")) },
            {
                validateSealedEncodingSurface(
                    "UTF-8",
                    listOf("-Dfile.encoding=UTF-8"),
                    emptyMap<String, String>(),
                    listOf("-Dfile.encoding=UTF-8", "-Dfile.encoding=UTF-8"),
                )
            },
            {
                validateSealedEncodingSurface(
                    "UTF-8",
                    emptyList(),
                    mapOf("file.encoding" to "UTF-8"),
                    listOf("-Dfile.encoding=UTF-8"),
                )
            },
            { validateSealedEncodingSurface("UTF-8", emptyList(), emptyMap<String, String>(), emptyList()) },
            {
                validateSealedEncodingSurface(
                    "UTF-8",
                    emptyList(),
                    emptyMap<String, String>(),
                    listOf("-Dfile.encoding=UTF-8", "-Dfile.encoding=UTF-8"),
                )
            },
        )
        mutations.forEachIndexed { index, mutation ->
            val failure = assertThrows("encoding mutation $index", GradleException::class.java, mutation)
            assertTrue(failure.message, failure.message.orEmpty().contains("file.encoding"))
        }
    }

    @Test
    fun executableSurfaceRequiresTheExactLauncherExecutableAndRejectsAbsence() {
        val launcher = File(System.getProperty("java.home"), "bin/java").canonicalFile

        validateSealedExecutable(launcher.absolutePath, launcher)
        listOf(null, File(launcher.parentFile, "alternate-java").absolutePath).forEach { executable ->
            val failure =
                assertThrows(GradleException::class.java) {
                    validateSealedExecutable(executable, launcher)
                }
            assertTrue(failure.message, failure.message.orEmpty().contains("executable/javaLauncher"))
        }
    }

    @Test
    fun exactProjectMappingAndEveryRealPitOptionAliasAreClosedInProcess() {
        listOf(":domain:station", ":domain:location", ":domain:settings")
            .forEach(::requireSupportedMutationProject)
        val failure = assertThrows(GradleException::class.java) {
            requireSupportedMutationProject(":core:network")
        }
        assertTrue(failure.message, failure.message.orEmpty().contains("gasstation.jvm.mutation supports exactly"))

        listOf(
            Triple(listOf("fixture"), null, null) to "--targetTests",
            Triple(null, listOf("+fixture"), null) to "--additionalFeatures",
            Triple(null, null, true) to "--verbose",
        ).forEach { (override, surface) ->
            val optionFailure = assertThrows(GradleException::class.java) {
                validatePitestOptionOverrides(override.first, override.second, override.third)
            }
            assertTrue(optionFailure.message, optionFailure.message.orEmpty().contains(surface))
        }
    }

    @Test
    fun directPitestTypedActionAlwaysRejects() {
        val task = ProjectBuilder.builder().build().tasks.create("pitest")
        val failure = assertThrows(GradleException::class.java) {
            RejectDirectPitestAction(":domain:station:pitestVerified").execute(task)
        }

        assertTrue(
            failure.message,
            failure.message.orEmpty().contains("Direct pitest is unsupported; use :domain:station:pitestVerified"),
        )
    }

    @Test
    fun canonicalEffectiveSurfaceRejectsEveryChangedOrAddedMutationProducingField() {
        val expected = sortedMapOf(
            "pit.mutators" to "DEFAULTS",
            "pit.targetClasses" to "com.gasstation.domain.station.*",
            "pit.sourceDirs" to "repo:domain/station/src/main/kotlin:directory",
            "pit.launchClasspath" to "external:pitest.jar:abc",
            "derivedCli.mutators" to "DEFAULTS",
            "java.argumentProviderCount" to "0",
            "java.environment" to "CI=true",
        )
        validateEffectivePitestSurface(expected, expected)
        expected.keys.forEach { field ->
            val changed = expected.toMutableMap().also { it[field] = "changed" }
            val failure = assertThrows(GradleException::class.java) {
                validateEffectivePitestSurface(expected, changed)
            }
            assertTrue(failure.message, failure.message.orEmpty().contains(field))
        }
        val added = expected + ("unknown.child.option" to "enabled")
        val failure = assertThrows(GradleException::class.java) {
            validateEffectivePitestSurface(expected, added)
        }
        assertTrue(failure.message, failure.message.orEmpty().contains("unknown.child.option"))
    }

    @Test
    fun dedicatedGradleCacheDependenciesUseLocationNeutralContentIdentity() {
        val root = temporaryFolder.newFolder("identity-root")
        val dependency = File(
            root,
            "build/quality/pitest-runtime/gradle-user-home/caches/modules-2/files-2.1/example/dependency.jar",
        )
        dependency.parentFile.mkdirs()
        dependency.writeBytes("stable dependency".toByteArray())

        val identity = canonicalIdentity(dependency, root)

        assertTrue(identity, identity.startsWith("external:dependency.jar:"))
        assertFalse(identity, identity.contains("gradle-user-home"))
    }

    @Test
    fun realTestKitRejectsThreePitAliasesArgsAndDebugJvmBeforePitExecution() {
        val project = mutationProject("real-options")
        val cases = listOf(
            listOf("--targetTests", "fixture.TargetTest") to "--targetTests",
            listOf("--additionalFeatures", "+fixture") to "--additionalFeatures",
            listOf("--verbose") to "--verbose",
            listOf("--args=--mutators=EMPTY_RETURNS") to "--args",
            listOf("--debug-jvm") to "debug",
        )
        cases.forEachIndexed { index, (option, diagnostic) ->
            val result = project.runner(
                ":domain:station:pitestVerified",
                "-x", ":domain:station:verifyPitestConfiguration",
                *option.toTypedArray(),
            ).buildAndFail()
            assertTrue("case $index missing $diagnostic: ${result.output}", result.output.contains(diagnostic))
            assertFalse("case $index unexpectedly reached PIT", result.output.contains("PIT >>"))
        }
        val lateMutations = listOf(
            """
            tasks.named<JavaExec>("pitestVerified") {
                argumentProviders.add(org.gradle.process.CommandLineArgumentProvider { throw GradleException("PROVIDER_INVOKED") })
            }
            """.trimIndent() to "java.argumentProviderCount",
            """
            tasks.named<JavaExec>("pitestVerified") {
                jvmArgumentProviders.add(org.gradle.process.CommandLineArgumentProvider { throw GradleException("PROVIDER_INVOKED") })
            }
            """.trimIndent() to "java.jvmArgumentProviderCount",
            """
            tasks.named<JavaExec>("pitestVerified") { isIgnoreExitValue = true }
            """.trimIndent() to "java.ignoreExitValue",
            """
            tasks.named<JavaExec>("pitestVerified") { defaultCharacterEncoding = "UTF-16" }
            """.trimIndent() to "java.defaultCharacterEncoding",
            """
            tasks.named<JavaExec>("pitestVerified") { environment("SECRET_TOKEN", "must-not-serialize") }
            """.trimIndent() to "execution surface: environment",
            """
            tasks.named("pitestVerified") {
                (this as info.solidsoft.gradle.pitest.PitestTask).mutators.set(setOf("EMPTY_RETURNS"))
            }
            """.trimIndent() to "pit.mutators",
            """
            gradle.projectsEvaluated {
                tasks.named("pitestVerified") {
                    (this as info.solidsoft.gradle.pitest.PitestTask).sourceDirs.from(layout.projectDirectory.dir("late-source"))
                }
            }
            """.trimIndent() to "pit.sourceDirs",
            """
            gradle.projectsEvaluated {
                tasks.named("pitestVerified") {
                    (this as info.solidsoft.gradle.pitest.PitestTask).mutableCodePaths.from(layout.projectDirectory.dir("late-mutable"))
                }
            }
            """.trimIndent() to "pit.mutableCodePaths",
            """
            gradle.projectsEvaluated {
                tasks.named("pitestVerified") {
                    (this as info.solidsoft.gradle.pitest.PitestTask).additionalClasspath.from(layout.projectDirectory.file("late-additional.jar"))
                }
            }
            """.trimIndent() to "pit.additionalClasspath",
            """
            gradle.projectsEvaluated {
                tasks.named("pitestVerified") {
                    (this as info.solidsoft.gradle.pitest.PitestTask).launchClasspath.from(layout.projectDirectory.file("late-launch.jar"))
                }
            }
            """.trimIndent() to "execution surface: classpath",
        )
        lateMutations.forEachIndexed { index, (mutation, diagnostic) ->
            project.writeFile("domain/station/build.gradle.kts", mutationBuildScript(mutation))
            val result = project.runner(
                ":domain:station:pitestVerified",
                "-x", ":domain:station:verifyPitestConfiguration",
            ).buildAndFail()
            assertTrue("late case $index missing $diagnostic: ${result.output}", result.output.contains(diagnostic))
            assertFalse("late case $index invoked a provider", result.output.contains("PROVIDER_INVOKED"))
            assertFalse("late case $index serialized an injected value", result.output.contains("must-not-serialize"))
        }
        project.writeFile("domain/station/build.gradle.kts", mutationBuildScript(""))
        val nonZero = project.runner(
            ":domain:station:pitestVerified",
            "-x", ":domain:station:verifyPitestConfiguration",
        ).buildAndFail()
        assertTrue("canonical non-zero PIT launch was unexpectedly ignored: ${nonZero.output}", nonZero.output.contains("PIT"))
        assertFalse("canonical non-zero PIT launch reported success", nonZero.output.contains("BUILD SUCCESSFUL"))
    }

    private fun mutationProject(name: String): GradlePluginTestProject {
        val project = GradlePluginTestProject.create(temporaryFolder.newFolder(name), sharedGradleUserHome)
        project.writeSettings(
            """
            rootProject.name = "mutation-fixture"
            pluginManagement { repositories { gradlePluginPortal(); mavenCentral(); google() } }
            dependencyResolutionManagement { repositories { mavenCentral(); google() } }
            include(":domain:station", ":domain:location", ":domain:settings")
            """.trimIndent(),
        )
        project.writeFile(
            "gradle/libs.versions.toml",
            """
            [versions]
            kotlin = "2.4.10"
            [libraries]
            kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin" }
            """.trimIndent(),
        )
        listOf("station", "location", "settings").forEach { module ->
            project.writeFile(
                "domain/$module/build.gradle.kts",
                mutationBuildScript(),
            )
            project.writeFile(
                "domain/$module/src/main/kotlin/com/gasstation/domain/$module/Fixture.kt",
                "package com.gasstation.domain.$module\npublic class Fixture { public fun answer(): Int = 1 }",
            )
        }
        return project
    }

    private fun mutationBuildScript(extra: String = ""): String =
        """
        import org.gradle.api.tasks.JavaExec
        plugins { id("gasstation.jvm.mutation") }
        dependencies { testImplementation(kotlin("test")) }
        $extra
        """.trimIndent()
}
