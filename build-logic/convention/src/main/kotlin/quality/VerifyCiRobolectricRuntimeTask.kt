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
        val ciJavaVersion =
            CI_JAVA_VERSION.find(workflow)?.groupValues?.get(1)?.toInt()
                ?: throw GradleException(
                    "Android CI must declare a top-level CI_JAVA_VERSION for the shared Gradle runtime.",
                )
        val javaVersionDeclarations =
            JAVA_VERSION_DECLARATION.findAll(workflow).map { it.groupValues[1] }.toList()
        if (
            javaVersionDeclarations.isEmpty() ||
            javaVersionDeclarations.any { it != EXPECTED_JAVA_VERSION_REFERENCE }
        ) {
            throw GradleException(
                "Every Android CI setup-java step must use $EXPECTED_JAVA_VERSION_REFERENCE; found " +
                    javaVersionDeclarations.joinToString(),
            )
        }
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
            Regex("(?m)^\\s*CI_JAVA_VERSION:\\s*[\\\"]?(\\d+)[\\\"]?\\s*$")
        private val JAVA_VERSION_DECLARATION = Regex("(?m)^\\s*java-version:\\s*(.+?)\\s*$")
        private const val EXPECTED_JAVA_VERSION_REFERENCE = "\${{ env.CI_JAVA_VERSION }}"
    }
}
