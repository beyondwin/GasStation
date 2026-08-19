package com.gasstation.buildlogic.quality.coverage

import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.testing.jacoco.tasks.JacocoReport

/** Typed, fail-closed JaCoCo XML producer for one exact report/test identity. */
abstract class CoverageXmlReportTask : JacocoReport() {
    @get:Input abstract val reportIdentity: Property<String>
    @get:Input abstract val expectedTestTaskPath: Property<String>
    @get:Input abstract val observedTestTaskPath: Property<String>
    @get:Input abstract val selectedTestTaskCount: Property<Int>

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val preparedClassDirectory: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val authoredSourceDirectories: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val exactExecutionData: ConfigurableFileCollection

    @TaskAction
    override fun generate() {
        val executionFiles = exactExecutionData.files.filter { it.isFile }
        val hasPreparedClass =
            preparedClassDirectory.get().asFile.walkTopDown()
                .none { it.isFile && it.extension == "class" }
                .not()
        validateCoverageXmlReportInputs(
            reportIdentity.get(),
            selectedTestTaskCount.get(),
            observedTestTaskPath.get(),
            expectedTestTaskPath.get(),
            executionFiles.size,
            hasPreparedClass,
        )
        super.generate()
    }
}

internal fun validateCoverageXmlReportInputs(
    reportIdentity: String,
    selectedTestTaskCount: Int,
    observedTestTaskPath: String,
    expectedTestTaskPath: String,
    executionFileCount: Int,
    hasPreparedClass: Boolean,
) {
    if (selectedTestTaskCount != 1) {
        throw GradleException(
            "$reportIdentity requires exactly one selected unit-test task; found $selectedTestTaskCount",
        )
    }
    if (observedTestTaskPath != expectedTestTaskPath) {
        throw GradleException(
            "$reportIdentity test task identity mismatch: $observedTestTaskPath != $expectedTestTaskPath",
        )
    }
    if (executionFileCount != 1) {
        throw GradleException(
            "$reportIdentity requires exactly one existing JaCoCo execution file; found $executionFileCount",
        )
    }
    if (!hasPreparedClass) throw GradleException("$reportIdentity prepared class directory is empty")
}
