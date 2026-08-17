package com.gasstation.buildlogic.quality.coverage

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class WriteCoverageManifestIndexTask : DefaultTask() {
    @get:Input abstract val sourceCommit: Property<String>
    @get:Input abstract val gradleProjects: ListProperty<String>
    @get:Input abstract val buildModules: ListProperty<String>
    @get:Input abstract val reviewedExcludedModules: ListProperty<String>
    @get:Internal abstract val repositoryRoot: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val entryFiles: ConfigurableFileCollection

    @get:OutputFile abstract val outputFile: RegularFileProperty

    @TaskAction
    fun writeIndex() {
        val root = repositoryRoot.get().asFile.canonicalFile
        val entries = entryFiles.files.sortedBy { relativePath(root, it) }
        val entryPaths = entries.map { relativePath(root, it) }
        if (entryPaths.size != entryPaths.toSet().size) throw GradleException("Duplicate coverage manifest entry path")
        val entryModules = entries.map { file ->
            Regex("\"module\":\"([^\"]+)\"").find(file.readText())?.groupValues?.get(1)
                ?: throw GradleException("Coverage manifest entry has no module: ${relativePath(root, file)}")
        }.toSet()
        val unowned = buildModules.get().toSet() - entryModules - reviewedExcludedModules.get().toSet()
        if (unowned.isNotEmpty()) {
            throw GradleException("${unowned.sorted().joinToString()} must have a coverage owner or reviewed exclusion")
        }
        val commit = sourceCommit.get()
        entries.forEach { file ->
            if (!file.readText().contains("\"sourceCommit\":\"$commit\"")) {
                throw GradleException("Coverage entry source commit mismatch: ${relativePath(root, file)}")
            }
        }
        val payload = linkedMapOf<String, Any>(
            "schemaVersion" to 1,
            "sourceCommit" to commit,
            "gradleProjects" to gradleProjects.get().sorted(),
            "buildModules" to buildModules.get().sorted(),
            "entries" to entryPaths,
        )
        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            writeBytes(canonicalCoverageJson(payload) + byteArrayOf('\n'.code.toByte()))
        }
    }
}

private fun relativePath(root: File, file: File): String {
    val canonical = file.canonicalFile
    if (!canonical.toPath().startsWith(root.toPath())) throw GradleException("Coverage path escapes repository: $file")
    return canonical.relativeTo(root).invariantSeparatorsPath
}
