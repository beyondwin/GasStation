package com.gasstation.buildlogic.quality

import com.gasstation.buildlogic.quality.mutation.RejectDirectPitestAction
import com.gasstation.buildlogic.quality.mutation.configureSealedDebugOptions
import com.gasstation.buildlogic.quality.mutation.requireSupportedMutationProject
import info.solidsoft.gradle.pitest.validatePitestOptionOverrides
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.api.tasks.JavaExec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GasStationJvmMutationConventionPluginTest {
    @Test
    fun sealedDebugOptionsHaveNoUnsetInheritedSurface() {
        val task = ProjectBuilder.builder().build().tasks.create("sealedJava", JavaExec::class.java)

        configureSealedDebugOptions(task.debugOptions)

        assertFalse(task.debugOptions.enabled.get())
        assertEquals("localhost", task.debugOptions.host.get())
        assertEquals(5005, task.debugOptions.port.get())
        assertTrue(task.debugOptions.server.get())
        assertTrue(task.debugOptions.suspend.get())
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
