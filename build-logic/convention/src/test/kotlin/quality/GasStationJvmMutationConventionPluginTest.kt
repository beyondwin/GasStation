package com.gasstation.buildlogic.quality

import com.gasstation.buildlogic.quality.mutation.RejectDirectPitestAction
import com.gasstation.buildlogic.quality.mutation.configureSealedInheritedJavaExecDefaults
import com.gasstation.buildlogic.quality.mutation.blockingMutationThreshold
import com.gasstation.buildlogic.quality.mutation.requireSupportedMutationProject
import com.gasstation.buildlogic.quality.mutation.validateBlockingEnforcement
import info.solidsoft.gradle.pitest.validatePitestOptionOverrides
import info.solidsoft.gradle.pitest.validateSealedExecutable
import info.solidsoft.gradle.pitest.validateSealedEncodingSurface
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.api.tasks.JavaExec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GasStationJvmMutationConventionPluginTest {
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
}
