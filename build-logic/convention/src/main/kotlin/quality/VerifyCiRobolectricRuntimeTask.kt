package com.gasstation.buildlogic.quality

import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Verification-only task with no outputs")
abstract class VerifyCiRobolectricRuntimeTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val workflowFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val robolectricConfigFile: RegularFileProperty

    @TaskAction
    fun verify() {
        val workflow = workflowFile.get().asFile.readText()
        val runtimeVersions = CI_JAVA_VERSION.findAll(workflow).map { it.groupValues[1] }.toList()
        val toolchainVersions = CI_JAVA_TOOLCHAIN_VERSION.findAll(workflow).map { it.groupValues[1] }.toList()
        if (runtimeVersions != listOf(EXACT_RUNTIME_VERSION)) {
            throw GradleException("Android CI must declare exact CI_JAVA_VERSION=$EXACT_RUNTIME_VERSION once.")
        }
        if (toolchainVersions != listOf(EXACT_TOOLCHAIN_VERSION)) {
            throw GradleException("Android CI must declare exact CI_JAVA_TOOLCHAIN_VERSION=$EXACT_TOOLCHAIN_VERSION once.")
        }
        if (SETUP_JAVA.containsMatchIn(workflow)) {
            throw GradleException("Android CI may not use actions/setup-java; use the closed JDK installer action.")
        }
        if (!CLOSED_INSTALLER.containsMatchIn(workflow)) {
            throw GradleException("Android CI must use ./.github/actions/setup-build-inputs.")
        }
        if (RUNNER.findAll(workflow).map { it.groupValues[1] }.any { it != "ubuntu-24.04" }) {
            throw GradleException("Every Android CI job must use the explicit mutable ubuntu-24.04 runner label.")
        }
        val ciJavaVersion = runtimeVersions.single().substringBefore('.').toInt()
        val robolectricSdk =
            Properties().run {
                robolectricConfigFile.get().asFile.inputStream().use(::load)
                getProperty("sdk")?.toIntOrNull()
            } ?: throw GradleException(
                "config/robolectric/robolectric.properties must declare a numeric sdk.",
            )
        val minimumJavaVersion = if (robolectricSdk >= 36) 21 else 17

        if (ciJavaVersion < minimumJavaVersion) {
            throw GradleException(
                "Robolectric SDK $robolectricSdk requires Java $minimumJavaVersion or newer, " +
                    "but Android CI declares Java $ciJavaVersion.",
            )
        }
        logger.lifecycle(
            "CI/Robolectric runtime OK: Java $ciJavaVersion supports test SDK $robolectricSdk.",
        )
    }

    companion object {
        private val CI_JAVA_VERSION =
            Regex("(?m)^  CI_JAVA_VERSION:\\s*[\\\"]?([^\\\"\\s]+)[\\\"]?\\s*$")
        private val CI_JAVA_TOOLCHAIN_VERSION =
            Regex("(?m)^  CI_JAVA_TOOLCHAIN_VERSION:\\s*[\\\"]?([^\\\"\\s]+)[\\\"]?\\s*$")
        private val SETUP_JAVA = Regex("(?m)^\\s*-?\\s*uses:\\s*actions/setup-java@")
        private val CLOSED_INSTALLER =
            Regex("(?m)^\\s*-?\\s*uses:\\s*\\./\\.github/actions/setup-build-inputs\\s*$")
        private val RUNNER = Regex("(?m)^\\s{4}runs-on:\\s*([^\\s#]+)")
        private const val EXACT_RUNTIME_VERSION = "21.0.12.1+1"
        private const val EXACT_TOOLCHAIN_VERSION = "17.0.20+8"
    }
}
