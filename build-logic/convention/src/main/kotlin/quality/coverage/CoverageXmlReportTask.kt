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
        if (selectedTestTaskCount.get() != 1) {
            throw GradleException(
                "${reportIdentity.get()} requires exactly one selected unit-test task; " +
                    "found ${selectedTestTaskCount.get()}",
            )
        }
        if (observedTestTaskPath.get() != expectedTestTaskPath.get()) {
            throw GradleException(
                "${reportIdentity.get()} test task identity mismatch: " +
                    "${observedTestTaskPath.get()} != ${expectedTestTaskPath.get()}",
            )
        }
        val executionFiles = exactExecutionData.files.filter { it.isFile }
        if (executionFiles.size != 1) {
            throw GradleException(
                "${reportIdentity.get()} requires exactly one existing JaCoCo execution file; " +
                    "found ${executionFiles.size}",
            )
        }
        if (
            preparedClassDirectory.get().asFile.walkTopDown()
                .none { it.isFile && it.extension == "class" }
        ) {
            throw GradleException("${reportIdentity.get()} prepared class directory is empty")
        }
        super.generate()
    }
}
