package com.gasstation.buildlogic.quality.coverage

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

@CacheableTask
abstract class PrepareCoverageClassesTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputJars: ListProperty<RegularFile>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputDirectories: ListProperty<Directory>

    @get:Input
    abstract val excludedClassPatterns: ListProperty<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    @TaskAction
    fun prepare() {
        val output = outputDirectory.get().asFile
        val staging = output.resolveSibling("${output.name}.staging")
        deleteExact(staging)
        staging.mkdirs()
        val seen = linkedMapOf<String, String>()

        inputDirectories.get().map(Directory::getAsFile).sortedBy(File::getAbsolutePath).forEach { directory ->
            if (!directory.exists()) return@forEach
            directory.walkTopDown().filter(File::isFile).sortedBy { it.relativeTo(directory).invariantSeparatorsPath }
                .forEach { source ->
                    copyClass(source.relativeTo(directory).invariantSeparatorsPath, source.readBytes(), staging, seen)
                }
        }
        inputJars.get().map(RegularFile::getAsFile).sortedBy(File::getAbsolutePath).forEach { archive ->
            ZipFile(archive).use { zip ->
                zip.entries().asSequence().filterNot { it.isDirectory }.sortedBy { it.name }.forEach { entry ->
                    copyClass(entry.name, zip.getInputStream(entry).use { it.readBytes() }, staging, seen)
                }
            }
        }
        if (seen.isEmpty()) {
            deleteExact(staging)
            throw GradleException("Coverage class preparation produced zero class files for $path")
        }
        deleteExact(output)
        try {
            Files.move(staging.toPath(), output.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(staging.toPath(), output.toPath())
        } catch (error: Exception) {
            deleteExact(output)
            throw GradleException("Could not publish prepared coverage classes for $path", error)
        }
    }

    private fun copyClass(path: String, bytes: ByteArray, staging: File, seen: MutableMap<String, String>) {
        val normalized = path.replace('\\', '/')
        if (!normalized.endsWith(".class")) return
        if (normalized.startsWith('/') || normalized.split('/').any { it.isEmpty() || it == "." || it == ".." }) {
            throw GradleException("Malformed coverage class path: $path")
        }
        if (excludedClassPatterns.get().any { pattern -> coverageGlobMatches(pattern, normalized) }) return
        val collision = seen.entries.firstOrNull { it.key.equals(normalized, ignoreCase = true) }
        if (collision != null) {
            throw GradleException("Duplicate or case-colliding coverage class path: ${collision.key} and $normalized")
        }
        seen[normalized] = normalized
        staging.resolve(normalized).apply {
            parentFile.mkdirs()
            writeBytes(bytes)
            setLastModified(0L)
        }
    }

    private fun deleteExact(file: File) {
        if (!file.exists()) return
        fileSystemOperations.delete { delete(file) }
        if (file.exists()) throw GradleException("Could not delete coverage path: $file")
    }
}

private fun coverageGlobMatches(pattern: String, path: String): Boolean {
    val regex = buildString {
        append('^')
        var index = 0
        while (index < pattern.length) {
            when {
                pattern.startsWith("**", index) -> {
                    append(".*")
                    index += 2
                }
                pattern[index] == '*' -> {
                    append("[^/]*")
                    index++
                }
                else -> {
                    append(Regex.escape(pattern[index].toString()))
                    index++
                }
            }
        }
        append('$')
    }
    return Regex(regex).matches(path)
}
