package com.gasstation.buildlogic.quality

import com.gasstation.buildlogic.quality.mutation.RejectDirectPitestAction
import com.gasstation.buildlogic.quality.mutation.requireSupportedMutationProject
import info.solidsoft.gradle.pitest.validatePitestOptionOverrides
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GasStationJvmMutationConventionPluginTest {
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
